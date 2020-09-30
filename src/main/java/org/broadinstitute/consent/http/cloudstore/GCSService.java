package org.broadinstitute.consent.http.cloudstore;

import com.google.api.client.http.GenericUrl;
import com.google.api.gax.paging.Page;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.ws.rs.NotFoundException;
import org.apache.commons.io.IOUtils;
import org.broadinstitute.consent.http.configurations.StoreConfiguration;
import org.broadinstitute.consent.http.service.WhitelistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GCSService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private StoreConfiguration config;
    private Storage storage;

    public GCSService() {
    }

    @Inject
    public GCSService(StoreConfiguration config) {
        this.config = config;
        try {
            ServiceAccountCredentials credentials = ServiceAccountCredentials.
                    fromStream(new FileInputStream(config.getPassword()));
            Storage storage = StorageOptions.newBuilder().
                    setProjectId(credentials.getProjectId()).
                    setCredentials(credentials).
                    build().
                    getService();
            this.setStorage(storage);
        } catch (Exception e) {
            logger.error("Exception initializing GCSService: " + e.getMessage());
        }
    }

    @VisibleForTesting
    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    @VisibleForTesting
    public void setConfig(StoreConfiguration config) {
        this.config = config;
    }

    public GenericUrl postWhitelist(String content, String fileName) {
        BlobId blobId = BlobId.of(config.getBucket(), "whitelist/" + fileName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("text/plain").build();
        Blob blob = storage.create(blobInfo, content.getBytes());
        return new GenericUrl(blob.getMediaLink());
    }

    public String getMostRecentWhitelist() throws Exception {
        try {
            Optional<Blob> whitelist = listWhitelistItems().stream().findFirst();
            if (whitelist.isPresent()) {
                return new String(whitelist.get().getContent());
            } else {
                logger.error("Most recent whitelist does not exist.");
                throw new Exception("Most recent whitelist does not exist.");
            }
        } catch (Exception e) {
            logger.error("Error getting most recent whitelist: " + e.getMessage());
            throw new Exception("Error getting most recent whitelist: " + e.getMessage());
        }
    }

    private List<Blob> listWhitelistItems() {
        Bucket bucket = storage.get(config.getBucket());
        Page<Blob> blobs = bucket.list();
        List<Blob> matchingBlobs = StreamSupport.
                stream(blobs.iterateAll().spliterator(), false).
                filter(b -> !b.isDirectory()).
                filter(b -> b.getName().contains(WhitelistService.WHITELIST_FILE_PREFIX)).
                collect(Collectors.toList());
        Comparator<Blob> comparator = Comparator.comparing(BlobInfo::getCreateTime);
        Comparator<Blob> reversed = comparator.reversed();
        matchingBlobs.sort(reversed);
        return matchingBlobs;
    }

    /**
     * Store an input stream as a Blob
     *
     * @param content InputStream content
     * @param mediaType String media type
     * @param fileName String file name
     * @return BlobId of the stored document
     * @throws IOException Exception when storing document
     */
    public BlobId storeDocument(InputStream content, String mediaType, String fileName)
        throws IOException {
        byte[] bytes = IOUtils.toByteArray(content);
        BlobId blobId = BlobId.of(config.getBucket(), fileName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(mediaType).build();
        Blob blob = storage.create(blobInfo, bytes);
        return blob.getBlobId();
    }

    /**
     * Delete a document by Blob Id Name
     *
     * @param blobIdName String value of the document blob id name
     * @return True if document was deleted, false otherwise.
     */
    public boolean deleteDocument(String blobIdName) {
        Optional<Blob> blobOptional = getBlobFromUrl(blobIdName);
        return blobOptional
            .map(blob -> storage.delete(blob.getBlobId()))
            .orElse(false);
    }

    /**
     * Retrieve a document by Blob Id Name
     *
     * @param blobIdName String value of the document blob id name
     * @return InputStream of the document
     * @throws NotFoundException Returned when no document found
     */
    public InputStream getDocument(String blobIdName) throws NotFoundException {
        Optional<Blob> blobOptional = getBlobFromUrl(blobIdName);
        if (blobOptional.isPresent()) {
            return new ByteArrayInputStream(blobOptional.get().getContent());
        } else {
            throw new NotFoundException("Document Not Found: " + blobIdName);
        }
    }

    /**
     * Find a blob in the current storage bucket.
     *
     * @param blobIdName String value of the document blob id name
     * @return Optional<Blob>
     */
    private Optional<Blob> getBlobFromUrl(String blobIdName) {
        Blob blob = storage.get(BlobId.of(config.getBucket(), blobIdName));
        return Optional.of(blob);
    }
}
