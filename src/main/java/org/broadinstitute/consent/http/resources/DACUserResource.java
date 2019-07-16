package org.broadinstitute.consent.http.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.log4j.Logger;
import org.broadinstitute.consent.http.models.DACUser;
import org.broadinstitute.consent.http.models.Election;
import org.broadinstitute.consent.http.models.UserRole;
import org.broadinstitute.consent.http.models.dto.Error;
import org.broadinstitute.consent.http.models.user.ValidateDelegationResponse;
import org.broadinstitute.consent.http.service.AbstractElectionAPI;
import org.broadinstitute.consent.http.service.AbstractVoteAPI;
import org.broadinstitute.consent.http.service.ElectionAPI;
import org.broadinstitute.consent.http.service.VoteAPI;
import org.broadinstitute.consent.http.service.users.AbstractDACUserAPI;
import org.broadinstitute.consent.http.service.users.DACUserAPI;
import org.broadinstitute.consent.http.service.users.handler.DACUserRolesHandler;
import org.broadinstitute.consent.http.service.users.handler.UserRoleHandlerException;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Path("{api : (api/)?}dacuser")
public class DACUserResource extends Resource {

    private final DACUserAPI dacUserAPI;
    private final ElectionAPI electionAPI;
    private final VoteAPI voteAPI;
    protected final Logger logger() {
        return Logger.getLogger(this.getClass().getName());
    }

    public DACUserResource() {
        this.electionAPI = AbstractElectionAPI.getInstance();
        this.voteAPI = AbstractVoteAPI.getInstance();
        this.dacUserAPI = AbstractDACUserAPI.getInstance();
    }

    @POST
    @Consumes("application/json")
    @RolesAllowed(ADMIN)
    public Response createDACUser(@Context UriInfo info, String json) {
        DACUser dac = new DACUser(json);
        URI uri;
        DACUser dacUser;
        try {
            dacUser = dacUserAPI.createDACUser(dac);
            if (isChairPerson(dacUser.getRoles())) {
                dacUserAPI.updateExistentChairPersonToAlumni(dacUser.getDacUserId());
                List<Election> elections = electionAPI.cancelOpenElectionAndReopen();
                voteAPI.createVotesForElections(elections, true);
            }
            uri = info.getRequestUriBuilder().path("{email}").build(dacUser.getEmail());
            return Response.created(uri).entity(dacUser).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(new Error(e.getMessage(), Response.Status.BAD_REQUEST.getStatusCode())).build();
        }
    }

    @GET
    @Produces("application/json")
    @RolesAllowed(ADMIN)
    public Collection<DACUser> describeAllUsers() {
        return dacUserAPI.describeUsers();
    }

    @GET
    @Path("/{email}")
    @Produces("application/json")
    @PermitAll
    public DACUser describe(@PathParam("email") String email) {
        return dacUserAPI.describeDACUserByEmail(email);
    }

    // TODO: This is undocumented in swagger
    @PUT
    @Path("/{id}")
    @Consumes("application/json")
    @Produces("application/json")
    @RolesAllowed(ADMIN)
    public Response update(@Context UriInfo info, String json, @PathParam("id") Integer id) {
        Map<String, DACUser> userMap = constructUserMapFromJson(json);
        try {
            URI uri = info.getRequestUriBuilder().path("{id}").build(id);
            DACUser dacUser = dacUserAPI.updateDACUserById(userMap, id);
            getEmailPreferenceValue(json).ifPresent(aBoolean ->
                    dacUserAPI.updateEmailPreference(aBoolean, dacUser.getDacUserId())
            );
            return Response.ok(uri).entity(dacUser).build();
        } catch (UserRoleHandlerException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(new Error(e.getMessage(), Response.Status.BAD_REQUEST.getStatusCode())).build();
        } catch (Exception e) {
            return createExceptionResponse(e);
        }
    }

    @PUT
    @Path("/mainFields/{id}")
    @Consumes("application/json")
    @Produces("application/json")
    @PermitAll
    public Response updateMainFields(@Context UriInfo info, String json, @PathParam("id") Integer id) {
        DACUser dac = new DACUser(json);
        try {
            URI uri = info.getRequestUriBuilder().path("{id}").build(id);
            DACUser dacUser = dacUserAPI.updateDACUserById(dac, id);
            return Response.ok(uri).entity(dacUser).build();
        } catch (Exception e) {
            return createExceptionResponse(e);
        }
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{email}")
    @RolesAllowed(ADMIN)
    public Response delete(@PathParam("email") String email, @Context UriInfo info) {
        dacUserAPI.deleteDACUser(email);
        return Response.ok().entity("User was deleted").build();
    }


    @POST
    @Consumes("application/json")
    @Produces("application/json")
    @Path("/validateDelegation")
    @PermitAll
    public Response validateDelegation(@QueryParam("role") String role, String json) {
        DACUser dac = new DACUser(json);
        DACUser dacUser;
        try {
            dacUser = dacUserAPI.describeDACUserByEmail(dac.getEmail());
            ValidateDelegationResponse delegationResponse = dacUserAPI.validateNeedsDelegation(dacUser, role);
            return Response.ok().entity(delegationResponse).build();
        } catch (Exception e) {
            return createExceptionResponse(e);
        }
    }

    /**
     * For backwards compatibility with older clients, we manually handle the UserRole construction
     * instead of relying on Jersey to enforce object correctness.
     *
     * @param userId The DACUserId
     * @param json   A UserRole object that may or may not be correctly formed
     * @return An appropriate response
     */
    @PUT
    @Path("/status/{userId}")
    @Consumes("application/json")
    @Produces("application/json")
    @RolesAllowed(ADMIN)
    public Response updateStatus(@PathParam("userId") Integer userId, String json) {
        UserRole userRole = new UserRole(json);
        try {
            return Response.ok(dacUserAPI.updateRoleStatus(userRole, userId)).build();
        } catch (Exception e) {
            return createExceptionResponse(e);
        }
    }

    @GET
    @Path("/status/{userId}")
    @Consumes("application/json")
    @Produces("application/json")
    @RolesAllowed(ADMIN)
    public Response getUserStatus(@PathParam("userId") Integer userId) {
        try {
            return Response.ok(dacUserAPI.getRoleStatus(userId)).build();
        } catch (Exception e) {
            return createExceptionResponse(e);
        }
    }

    @PUT
    @Path("/name/{id}")
    @Consumes("application/json")
    @Produces("application/json")
    @RolesAllowed({ADMIN, RESEARCHER})
    public Response updateName(String json, @PathParam("id") Integer id) {
        DACUser user = new DACUser(json);
        try {
            DACUser dacUser = dacUserAPI.updateNameById(user, id);
            return Response.ok().entity(dacUser).build();
        } catch (Exception e) {
            return createExceptionResponse(e);
        }
    }

    private boolean isChairPerson(List<UserRole> userRoles) {
        boolean isChairPerson = false;
        for (UserRole role : userRoles) {
            if (role.getName().equalsIgnoreCase(CHAIRPERSON)) {
                isChairPerson = true;
                break;
            }
        }
        return isChairPerson;
    }

    /**
     * Convenience method to find the email preference from legacy json structure.
     *
     * @param json Raw json string from client
     * @return Optional value of the "emailPreference" boolean value set in the legacy json structure.
     */
    private Optional<Boolean> getEmailPreferenceValue(String json) {
        Optional<Boolean> aBoolean = Optional.empty();
        try {
            JsonObject jsonObject = new JsonParser().parse(json).getAsJsonObject();
            if (jsonObject.has("updatedUser") && !jsonObject.get("updatedUser").isJsonNull()) {
                JsonObject userObj = jsonObject.get("updatedUser").getAsJsonObject();
                if (userObj.has("roles") && !userObj.get("roles").isJsonNull()) {
                    List<JsonElement> rolesElements = new ArrayList<>();
                    userObj.get("roles").getAsJsonArray().forEach(rolesElements::add);
                    List<Boolean> emailPrefs = rolesElements.
                            stream().
                            filter(e -> e.getAsJsonObject().has("emailPreference")).
                            map(e -> e.getAsJsonObject().get("emailPreference").getAsBoolean()).
                            distinct().
                            collect(Collectors.toList());
                    // In practice, there should only be a single email preference value, if any.
                    if (emailPrefs.size() == 1) {
                        aBoolean = Optional.of(emailPrefs.get(0));
                    }
                }
            }
        } catch (Exception e) {
            logger().warn("Unable to extract email preference from: " + json + " : " + e.getMessage());
        }
        return aBoolean;
    }

    /**
     * Convenience method to handle legacy json user map structure
     *
     * @param json Raw json string from client
     * @return Map of operation to DACUser
     */
    private Map<String, DACUser> constructUserMapFromJson(String json) {
        JsonObject jsonObject = new JsonParser().parse(json).getAsJsonObject();
        Map<String, DACUser> userMap = new HashMap<>();
        JsonElement updatedUser = jsonObject.get(DACUserRolesHandler.UPDATED_USER_KEY);
        JsonElement delegatedUser = jsonObject.get(DACUserRolesHandler.DELEGATED_USER_KEY);
        JsonElement alternativeDataOwner = jsonObject.get(DACUserRolesHandler.ALTERNATIVE_OWNER_KEY);
        if (updatedUser != null && !updatedUser.isJsonNull()) {
            userMap.put(DACUserRolesHandler.UPDATED_USER_KEY, new DACUser(updatedUser.toString()));
        }
        if (delegatedUser != null && !delegatedUser.isJsonNull()) {
            userMap.put(DACUserRolesHandler.DELEGATED_USER_KEY, new DACUser(delegatedUser.toString()));
        }
        if (alternativeDataOwner != null && !alternativeDataOwner.isJsonNull()) {
            userMap.put(DACUserRolesHandler.ALTERNATIVE_OWNER_KEY, new DACUser(alternativeDataOwner.toString()));
        }
        return userMap;
    }

}
