package org.broadinstitute.consent.http.resources;

import org.broadinstitute.consent.http.service.AbstractConsentAPI;
import org.broadinstitute.consent.http.service.ConsentAPI;

import javax.annotation.security.PermitAll;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

@Path("{auth: (basic/|api/)?}consent")
public class ConsentManageResource extends Resource {

    private final ConsentAPI api;

    public ConsentManageResource() {
        this.api = AbstractConsentAPI.getInstance();
    }

    @GET
    @Path("/manage")
    @PermitAll
    public Response getConsentManage() {
        return Response.ok(api.describeConsentManage())
                .build();
    }

    @GET
    @Path("/unreviewed")
    @PermitAll
    public Response getTotalUnreviewedConsent() {
        return Response.ok("{\"dulUnReviewedCases\":"+api.getUnReviewedConsents()+"}").build();
    }


}
