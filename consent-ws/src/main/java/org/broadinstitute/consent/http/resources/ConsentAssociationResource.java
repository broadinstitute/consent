package org.broadinstitute.consent.http.resources;

import org.apache.log4j.Logger;
import org.broadinstitute.consent.http.models.ConsentAssociation;
import org.broadinstitute.consent.http.models.dto.Error;
import org.broadinstitute.consent.http.service.AbstractConsentAPI;
import org.broadinstitute.consent.http.service.ConsentAPI;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Path("{auth: (basic/|api/)?}consent/{id}/association")
public class ConsentAssociationResource extends Resource {

    private final ConsentAPI api;

    public ConsentAssociationResource() {
        this.api = AbstractConsentAPI.getInstance();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    public Response createAssociation(@PathParam("id") String consentId, ArrayList<ConsentAssociation> body) {
        try {
            String msg = String.format("POSTing association to id '%s' with body '%s'", consentId, body.toString());
            logger().debug(msg);
            List<ConsentAssociation> result = api.createAssociation(consentId, body);
            URI assocURI = buildConsentAssociationURI(consentId);
            return Response.ok(result).location(assocURI).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(new Error(String.format("Could not find consent with id %s", consentId), Response.Status.NOT_FOUND.getStatusCode())).build();
        }catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(new Error(e.getMessage(), Response.Status.BAD_REQUEST.getStatusCode())).build();
        }catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new Error(e.getMessage(), Response.Status.BAD_REQUEST.getStatusCode())).build();
        }
    }

    private URI buildConsentAssociationURI(String id) {
        return UriBuilder.fromResource(ConsentAssociationResource.class).build("api/", id);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    public Response updateAssociation(@PathParam("id") String consentId, ArrayList<ConsentAssociation> body) {
        try {
            String msg = String.format("PUTing association to id '%s' with body '%s'", consentId, body.toString());
            logger().debug(msg);
            List<ConsentAssociation> result = api.updateAssociation(consentId, body);
            URI assocURI = buildConsentAssociationURI(consentId);
            return Response.ok(result).location(assocURI).build();
        }  catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(new Error(String.format("Could not find consent with id %s", consentId), Response.Status.NOT_FOUND.getStatusCode())).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(new Error(e.getMessage(), Response.Status.BAD_REQUEST.getStatusCode())).build();
        }catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new Error(e.getMessage(), Response.Status.BAD_REQUEST.getStatusCode())).build();
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public Response getAssociation(@PathParam("id") String consentId, @QueryParam("associationType") String atype, @QueryParam("id") String objectId) {
        try {
            String msg = String.format("GETing association for id '%s' with associationType='%s' and id='%s'", consentId, (atype == null ? "<null>" : atype), (objectId == null ? "<null>" : objectId));
            logger().debug(msg);
            if (atype == null && objectId != null)
                return Response.status(Response.Status.BAD_REQUEST).build();
            List<ConsentAssociation> result = api.getAssociation(consentId, atype, objectId);
            URI assocURI = buildConsentAssociationURI(consentId);
            return Response.ok(result).location(assocURI).build();
        } catch (Exception e) {
            return Response.status(Response.Status.NOT_FOUND).entity(new Error(String.format("Could not find consent with id %s", consentId), Response.Status.NOT_FOUND.getStatusCode())).build();
        }
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    public Response deleteAssociation(@PathParam("id") String consentId, @QueryParam("associationType") String atype, @QueryParam("id") String objectId) {
        try {
            String msg = String.format("DELETEing association for id '%s' with associationType='%s' and id='%s'", consentId, (atype == null ? "<null>" : atype), (objectId == null ? "<null>" : objectId));
            logger().debug(msg);
            if (atype == null && objectId != null)
                return Response.status(Response.Status.BAD_REQUEST).build();
            List<ConsentAssociation> result = api.deleteAssociation(consentId, atype, objectId);
            URI assocURI = buildConsentAssociationURI(consentId);
            return Response.ok(result).location(assocURI).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(new Error(String.format("Could not find consent with id %s", consentId), Response.Status.NOT_FOUND.getStatusCode())).build();
        }catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(new Error(e.getMessage(), Response.Status.BAD_REQUEST.getStatusCode())).build();
        }
    }

    @Override
    protected Logger logger() {
        return Logger.getLogger("ConsentAssociationResource");
    }
}
