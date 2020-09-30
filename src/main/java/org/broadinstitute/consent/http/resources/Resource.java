package org.broadinstitute.consent.http.resources;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import org.apache.commons.io.IOUtils;
import org.broadinstitute.consent.http.enumeration.UserRoles;
import org.broadinstitute.consent.http.exceptions.UpdateConsentException;
import org.broadinstitute.consent.http.models.User;
import org.broadinstitute.consent.http.models.dto.Error;
import org.broadinstitute.consent.http.service.UnknownIdentifierException;
import org.broadinstitute.consent.http.service.users.handler.UserRoleHandlerException;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.owasp.fileio.FileValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by egolin on 9/17/14.
 * <p/>
 * Abstract superclass for all Resources.
 */
abstract public class Resource {

    // Resource based role names
    public final static String ADMIN = "Admin";
    public final static String ALUMNI = "Alumni";
    public final static String CHAIRPERSON = "Chairperson";
    public final static String DATAOWNER = "DataOwner";
    public final static String MEMBER = "Member";
    public final static String RESEARCHER = "Researcher";

    protected Logger logger() {
        return LoggerFactory.getLogger(this.getClass());
    }

    protected Response createExceptionResponse(Exception e) {
        try {
            logger().warn("Returning error response to client: " + e.getMessage());
            ExceptionHandler handler = dispatch.get(e.getClass());
            if (handler != null) {
                return handler.handle(e);
            } else {
                return Response.serverError().type(MediaType.APPLICATION_JSON).entity(new Error(e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())).build();
            }
        } catch (Throwable t) {
            logger().error(t.getMessage());
            return Response.serverError().type(MediaType.APPLICATION_JSON).entity(new Error(e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())).build();
        }
    }

    StreamingOutput createStreamingOutput(InputStream inputStream) {
        return output -> {
            try {
                output.write(IOUtils.toByteArray(inputStream));
            } catch (Exception e) {
                logger().error(e.getMessage());
                throw e;
            }
        };
    }

    protected void validateFileDetails(FormDataContentDisposition fileDetail) {
        FileValidator validator = new FileValidator();
        boolean validName = validator.isValidFileName("validating uploaded file name", fileDetail.getFileName(), true);
        if (!validName) {
            throw new IllegalArgumentException("File name is invalid");
        }
        boolean validSize = validator.getMaxFileUploadSize() >= fileDetail.getSize();
        if (!validSize) {
            throw new IllegalArgumentException("File size is invalid. Max size is: " + validator.getMaxFileUploadSize()/1000000 + " MB");
        }
    }

    private interface ExceptionHandler {
        Response handle(Exception e);
    }

    private static final Map<Class, ExceptionHandler> dispatch = new HashMap<>();

    static {
        dispatch.put(UserRoleHandlerException.class, e ->
                Response.status(Response.Status.CONFLICT).type(MediaType.APPLICATION_JSON).entity(new Error(e.getMessage(), Response.Status.CONFLICT.getStatusCode())).build());
        dispatch.put(UnsupportedOperationException.class, e ->
                Response.status(Response.Status.CONFLICT).type(MediaType.APPLICATION_JSON).entity(new Error(e.getMessage(), Response.Status.CONFLICT.getStatusCode())).build());
        dispatch.put(IllegalArgumentException.class, e ->
                Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON).entity(new Error(e.getMessage(), Response.Status.BAD_REQUEST.getStatusCode())).build());
        dispatch.put(IOException.class, e ->
                Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON).entity(new Error(e.getMessage(), Response.Status.BAD_REQUEST.getStatusCode())).build());
        dispatch.put(BadRequestException.class, e ->
                Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON).entity(new Error(e.getMessage(), Response.Status.BAD_REQUEST.getStatusCode())).build());
        dispatch.put(NotAuthorizedException.class, e ->
                Response.status(Response.Status.UNAUTHORIZED).type(MediaType.APPLICATION_JSON).entity(new Error(e.getMessage(), Response.Status.UNAUTHORIZED.getStatusCode())).build());
        dispatch.put(ForbiddenException.class, e ->
                Response.status(Response.Status.FORBIDDEN).type(MediaType.APPLICATION_JSON).entity(new Error(e.getMessage(), Response.Status.FORBIDDEN.getStatusCode())).build());
        dispatch.put(NotFoundException.class, e ->
                Response.status(Response.Status.NOT_FOUND).type(MediaType.APPLICATION_JSON).entity(new Error(e.getMessage(), Response.Status.NOT_FOUND.getStatusCode())).build());
        dispatch.put(UnknownIdentifierException.class, e ->
                Response.status(Response.Status.NOT_FOUND).type(MediaType.APPLICATION_JSON).entity(new Error(e.getMessage(), Response.Status.NOT_FOUND.getStatusCode())).build());
        dispatch.put(UpdateConsentException.class, e ->
                Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON).entity(new Error(e.getMessage(), Response.Status.BAD_REQUEST.getStatusCode())).build());
        dispatch.put(SQLSyntaxErrorException.class, e ->
                Response.serverError().type(MediaType.APPLICATION_JSON).entity(new Error("Database Error", Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())).build());
        dispatch.put(SQLException.class, e ->
                Response.serverError().type(MediaType.APPLICATION_JSON).entity(new Error("Database Error", Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())).build());
        dispatch.put(Exception.class, e ->
                Response.serverError().type(MediaType.APPLICATION_JSON).entity(new Error(e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())).build());

    }

    /**
     * Validate that the current authenticated user can access this resource.
     * If the user has one of the provided roles, then access is allowed.
     * If not, then the authenticated user must have the same identity as the
     * `userId` parameter they are requesting information for.
     *
     * @param authedRoles   List of UserRoles enums
     * @param authedUser The authenticated DACUser
     * @param userId        The id of the DACUser the authenticated user is requesting access to
     */
    void validateAuthedRoleUser(final List<UserRoles> authedRoles, final User authedUser, final Integer userId) {
        List<Integer> authedRoleIds = authedRoles.stream().
                map(UserRoles::getRoleId).
                collect(Collectors.toList());
        boolean authedUserHasRole = authedUser.getRoles().stream().
                anyMatch(userRole -> authedRoleIds.contains(userRole.getRoleId()));
        if (!authedUserHasRole && !authedUser.getDacUserId().equals(userId)) {
            throw new ForbiddenException("User does not have permission");
        }
    }

}
