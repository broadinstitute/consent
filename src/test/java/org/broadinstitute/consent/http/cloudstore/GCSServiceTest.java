package org.broadinstitute.consent.http.cloudstore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.client.http.GenericUrl;
import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.List;
import java.util.Spliterator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.ws.rs.core.MediaType;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.broadinstitute.consent.http.configurations.StoreConfiguration;
import org.broadinstitute.consent.http.service.WhitelistService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


public class GCSServiceTest {

    @Mock
    private Storage storage;

    @Mock
    private Blob blob;

    @Mock
    private Bucket bucket;

    @Mock
    private Page<Blob> blobs;

    @Mock
    private Iterable<Blob> iterable;

    private StoreConfiguration config;

    private GCSService service;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        config = new StoreConfiguration();
        config.setBucket("bucket");
        config.setEndpoint("http://localhost/");
        config.setPassword("password");
    }

    private void initStore() {
        service = new GCSService();
        service.setStorage(storage);
        service.setConfig(config);
    }

    @Test
    public void testPostWhitelist() {
        String filename = "filename.txt";
        when(blob.getMediaLink()).thenReturn(config.getEndpoint() + filename);
        when(storage.create(any(BlobInfo.class), any())).thenReturn(blob);
        when(storage.create(any(BlobInfo.class), any(), new Storage.BlobTargetOption[0])).thenReturn(blob);
        initStore();

        GenericUrl url = service.postWhitelist("content", filename);
        assertNotNull(url);
    }

    @Test
    public void testGetMostRecentWhitelist() throws Exception {
        int count = 3; // Count value is used in Blob name, create time, and content
        Spliterator<Blob> blobSpliterator = makeWhitelistBlobSpliterator(count);
        when(iterable.spliterator()).thenReturn(blobSpliterator);
        when(blobs.iterateAll()).thenReturn(iterable);
        when(bucket.list()).thenReturn(blobs);
        when(storage.get(any(String.class))).thenReturn(bucket);

        initStore();
        String whitelistData = service.getMostRecentWhitelist();
        assertNotNull(whitelistData);
        assertTrue(whitelistData.contains(String.valueOf(count)));
    }

    @Test
    public void testStoreDocument() throws Exception {
        String filename = "filename.txt";
        BlobId blobId = BlobId.of(config.getEndpoint(), filename);
        when(blobId.getName()).thenReturn(filename);
        when(blob.getBlobId()).thenReturn(blobId);
        when(storage.create(any(BlobInfo.class), any())).thenReturn(blob);
        when(storage.create(any(BlobInfo.class), any(), new Storage.BlobTargetOption[0])).thenReturn(blob);
        initStore();

        InputStream is = IOUtils.toInputStream("content", Charset.defaultCharset());
        BlobId storedBlobId = service.storeDocument(is, MediaType.TEXT_PLAIN, filename);
        assertNotNull(storedBlobId);
    }

    @Test
    public void testGetDocument() throws Exception {
        String fileName = RandomStringUtils.random(10, true, false);
        String fileContent = "content";
        String urlString = "http://localhost/bucket/" + fileName;
        Date now = new Date();
        Blob blob = mock(Blob.class);
        BlobId blobId = BlobId.of("bucket", fileName);
        when(blob.isDirectory()).thenReturn(false);
        when(blob.getMediaLink()).thenReturn(urlString);
        when(blob.getName()).thenReturn("bucket/" + fileName);
        when(blob.getCreateTime()).thenReturn(now.getTime());
        when(blob.getContent()).thenReturn((fileContent).getBytes());
        when(blob.getBlobId()).thenReturn(blobId);
        when(storage.get(any(BlobId.class))).thenReturn(blob);

        initStore();
        InputStream is = service.getDocument(urlString);
        String content = IOUtils.toString(is, Charset.defaultCharset());
        assertNotNull(is);
        assertEquals(fileContent, content);
    }

    @Test
    public void testDeleteDocument() {
        when(storage.get(any(BlobId.class))).thenReturn(blob);
        when(storage.delete(any(BlobId.class))).thenReturn(true);
        initStore();
        boolean deleted = service.deleteDocument(RandomStringUtils.random(10));
        assertTrue(deleted);
    }

    /**
     * Make an iterator of Blobs so we can mock out what Google will return for data.
     * @return Iterator<Blob>
     */
    @SuppressWarnings("SameParameterValue")
    private Spliterator<Blob> makeWhitelistBlobSpliterator(int i) {
        List<Blob> blobs = IntStream.range(1, i + 1).
                mapToObj(this::makeWhitelistBlob).
                collect(Collectors.toList());
        return blobs.spliterator();
    }

    private Blob makeWhitelistBlob(int i) {
        Blob blob = mock(Blob.class);
        when(blob.isDirectory()).thenReturn(false);
        when(blob.getMediaLink()).thenReturn("http://localhost/bucket/blob" + i);
        when(blob.getName()).thenReturn("bucket/blob_" + i + "_" + WhitelistService.WHITELIST_FILE_PREFIX + RandomStringUtils.random(10, false, true));
        when(blob.getCreateTime()).thenReturn(Long.valueOf(i));
        when(blob.getContent()).thenReturn(("blob_" + i).getBytes());
        return blob;
    }

}
