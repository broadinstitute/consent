package org.broadinstitute.consent.http.resources;

import com.google.inject.Inject;
import io.dropwizard.auth.Auth;
import org.broadinstitute.consent.http.cloudstore.GCSStore;
import org.broadinstitute.consent.http.models.AuthUser;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

@Path("api/whitelist")
public class WhitelistResource extends Resource {

    private GCSStore gcsStore;

    private Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    @Inject
    public WhitelistResource(GCSStore gcsStore) {
        this.gcsStore = gcsStore;
    }

    @POST
    @RolesAllowed(ADMIN)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response postWhitelist(@Auth AuthUser user, @FormDataParam("fileData") String fileData) {
        // get a timestamp to label the file
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss");
        String timestamp = dateFormat.format(new Date());
        // push file to bucket
        String fileName = "lc_whitelist_"+timestamp+".tsv";
        try {
//            this.gcsStore.postWhitelist(InputStream fileData, String fileName);
            logger.info(fileData);
            return Response.ok(fileData).build();
        } catch (Exception e) {
            return createExceptionResponse(e);
        }
    }
}