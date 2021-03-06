package org.broadinstitute.consent.http.resources;

import com.google.cloud.storage.BlobId;
import com.google.inject.Inject;
import io.dropwizard.auth.Auth;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import org.broadinstitute.consent.http.cloudstore.GCSService;
import org.broadinstitute.consent.http.enumeration.DarDocumentType;
import org.broadinstitute.consent.http.enumeration.UserRoles;
import org.broadinstitute.consent.http.models.AuthUser;
import org.broadinstitute.consent.http.models.DataAccessRequest;
import org.broadinstitute.consent.http.models.DataAccessRequestData;
import org.broadinstitute.consent.http.models.DataAccessRequestManage;
import org.broadinstitute.consent.http.models.User;
import org.broadinstitute.consent.http.models.dto.Error;
import org.broadinstitute.consent.http.service.DataAccessRequestService;
import org.broadinstitute.consent.http.service.EmailNotifierService;
import org.broadinstitute.consent.http.service.MatchService;
import org.broadinstitute.consent.http.service.UserService;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("api/dar/v2")
public class DataAccessRequestResourceVersion2 extends Resource {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());
  private final DataAccessRequestService dataAccessRequestService;
  private final EmailNotifierService emailNotifierService;
  private final GCSService gcsService;
  private final MatchService matchService;
  private final UserService userService;

  @Inject
  public DataAccessRequestResourceVersion2(
      DataAccessRequestService dataAccessRequestService,
      EmailNotifierService emailNotifierService,
      GCSService gcsService,
      UserService userService,
      MatchService matchService) {
    this.dataAccessRequestService = dataAccessRequestService;
    this.emailNotifierService = emailNotifierService;
    this.gcsService = gcsService;
    this.userService = userService;
    this.matchService = matchService;
  }

  @GET
  @Produces("application/json")
  @PermitAll
  public Response getDataAccessRequests(@Auth AuthUser authUser) {
    try {
      User user = userService.findUserByEmail(authUser.getName());
      List<DataAccessRequest> dars = dataAccessRequestService.getDataAccessRequestsByUserRole(user);
      return Response.ok().entity(dars).build();
    } catch (Exception e) {
      return createExceptionResponse(e);
    }
  }

  @POST
  @Consumes("application/json")
  @Produces("application/json")
  @RolesAllowed(RESEARCHER)
  public Response createDataAccessRequest(
      @Auth AuthUser authUser, @Context UriInfo info, String dar) {
    try {
      User user = findUserByEmail(authUser.getName());
      DataAccessRequest newDar = populateDarFromJsonString(user, dar);
      List<DataAccessRequest> results =
          dataAccessRequestService.createDataAccessRequest(user, newDar);
      URI uri = info.getRequestUriBuilder().build();
      for (DataAccessRequest r : results) {
        matchService.reprocessMatchesForPurpose(r.getReferenceId());
        emailNotifierService.sendNewDARRequestMessage(
            r.getData().getDarCode(), r.getData().getDatasetIds());
      }
      return Response.created(uri)
          .entity(
              results.stream()
                  .map(DataAccessRequest::convertToSimplifiedDar)
                  .collect(Collectors.toList()))
          .build();
    } catch (Exception e) {
      return createExceptionResponse(e);
    }
  }

  @GET
  @Path("/{referenceId}")
  @Produces("application/json")
  @PermitAll
  public Response getByReferenceId(
      @Auth AuthUser authUser, @PathParam("referenceId") String referenceId) {
    validateAuthedRoleUser(
        Stream.of(UserRoles.ADMIN, UserRoles.CHAIRPERSON, UserRoles.MEMBER)
            .collect(Collectors.toList()),
        authUser, referenceId);
    try {
      DataAccessRequest dar = dataAccessRequestService.findByReferenceId(referenceId);
      if (Objects.nonNull(dar)) {
        return Response.status(Response.Status.OK).entity(dar.convertToSimplifiedDar()).build();
      }
      return Response.status(Response.Status.NOT_FOUND)
          .entity(
              new Error(
                  "Unable to find Data Access Request with reference id: " + referenceId,
                  Response.Status.NOT_FOUND.getStatusCode()))
          .build();
    } catch (Exception e) {
      return createExceptionResponse(e);
    }
  }

  @PUT
  @Path("/{referenceId}")
  @Produces("application/json")
  @RolesAllowed(RESEARCHER)
  public Response updateByReferenceId(
      @Auth AuthUser authUser, @PathParam("referenceId") String referenceId, String dar) {
    try {
      User user = findUserByEmail(authUser.getName());
      DataAccessRequest originalDar = dataAccessRequestService.findByReferenceId(referenceId);
      checkAuthorizedUpdateUser(user, originalDar);
      DataAccessRequestData data = DataAccessRequestData.fromString(dar);
      // Keep dar data reference id in sync with the dar until we fully deprecate
      // it in dar data.
      data.setReferenceId(originalDar.getReferenceId());
      originalDar.setData(data);
      DataAccessRequest updatedDar =
          dataAccessRequestService.updateByReferenceIdVersion2(user, originalDar);
      matchService.reprocessMatchesForPurpose(referenceId);
      return Response.ok().entity(updatedDar.convertToSimplifiedDar()).build();
    } catch (Exception e) {
      return createExceptionResponse(e);
    }
  }

  @GET
  @Produces("application/json")
  @Path("/draft")
  @RolesAllowed(RESEARCHER)
  public Response getDraftDataAccessRequests(@Auth AuthUser authUser) {
    try {
      User user = findUserByEmail(authUser.getName());
      List<DataAccessRequest> draftDars = dataAccessRequestService.findAllDraftDataAccessRequestsByUser(user.getDacUserId());
      return Response.ok().entity(draftDars).build();
    } catch (Exception e) {
      return createExceptionResponse(e);
    }
  }

  @GET
  @Produces("application/json")
  @Path("/draft/{referenceId}")
  @RolesAllowed(RESEARCHER)
  public Response getDraftDar(@Auth AuthUser authUser, @PathParam("referenceId") String id) {
    try {
      User user = findUserByEmail(authUser.getName());
      DataAccessRequest dar = dataAccessRequestService.findByReferenceId(id);
      if (dar.getUserId().equals(user.getDacUserId())) {
        return Response.ok().entity(dar).build();
      }
      throw new ForbiddenException("User does not have permission");
    } catch (Exception e) {
      return createExceptionResponse(e);
    }
  }

  @POST
  @Consumes("application/json")
  @Produces("application/json")
  @Path("/draft")
  @RolesAllowed(RESEARCHER)
  public Response createDraftDataAccessRequest(
      @Auth AuthUser authUser, @Context UriInfo info, String dar) {
    try {
      User user = findUserByEmail(authUser.getName());
      DataAccessRequest newDar = populateDarFromJsonString(user, dar);
      DataAccessRequest result =
          dataAccessRequestService.insertDraftDataAccessRequest(user, newDar);
      URI uri = info.getRequestUriBuilder().path("/" + result.getReferenceId()).build();
      return Response.created(uri).entity(result.convertToSimplifiedDar()).build();
    } catch (Exception e) {
      return createExceptionResponse(e);
    }
  }

  @GET
  @Produces("application/json")
  @Path("/draft/manage")
  @RolesAllowed(RESEARCHER)
  public Response getDraftManageDataAccessRequests(@Auth AuthUser authUser) {
    try {
      User user = findUserByEmail(authUser.getName());
      List<DataAccessRequestManage> partials = dataAccessRequestService.getDraftDataAccessRequestManage(user.getDacUserId());
      return Response.ok().entity(partials).build();
    } catch (Exception e) {
      return createExceptionResponse(e);
    }
  }

  @PUT
  @Consumes("application/json")
  @Produces("application/json")
  @Path("/draft/{referenceId}")
  @RolesAllowed(RESEARCHER)
  public Response updatePartialDataAccessRequest(
      @Auth AuthUser authUser, @PathParam("referenceId") String referenceId, String dar) {
    try {
      User user = findUserByEmail(authUser.getName());
      DataAccessRequest originalDar = dataAccessRequestService.findByReferenceId(referenceId);
      checkAuthorizedUpdateUser(user, originalDar);
      DataAccessRequestData data = DataAccessRequestData.fromString(dar);
      // Keep dar data reference id in sync with the dar until we fully deprecate
      // it in dar data.
      data.setReferenceId(originalDar.getReferenceId());
      originalDar.setData(data);
      DataAccessRequest updatedDar =
          dataAccessRequestService.updateByReferenceIdVersion2(user, originalDar);
      return Response.ok().entity(updatedDar.convertToSimplifiedDar()).build();
    } catch (Exception e) {
      return createExceptionResponse(e);
    }
  }

  @GET
  @Produces({MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON})
  @Path("/{referenceId}/irbDocument")
  @RolesAllowed({RESEARCHER})
  public Response getIrbDocument (
      @Auth AuthUser authUser,
      @PathParam("referenceId") String referenceId) {
    try {
      User user = findUserByEmail(authUser.getName());
      DataAccessRequest dar = getDarById(referenceId);
      checkAuthorizedUpdateUser(user, dar);
      if (Objects.nonNull(dar.getData().getIrbDocumentLocation()) &&
        Objects.nonNull(dar.getData().getIrbDocumentName())) {
        String blobIdName = dar.getData().getIrbDocumentLocation();
        String fileName = dar.getData().getIrbDocumentName();
        InputStream is = gcsService.getDocument(blobIdName);
        StreamingOutput stream = createStreamingOutput(is);
        return Response.ok(stream)
            .header("Content-Disposition", "attachment; filename=" + fileName)
            .build();
      }
      throw new NotFoundException();
    } catch (Exception e) {
      return createExceptionResponse(e);
    }
  }

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{referenceId}/irbDocument")
  @RolesAllowed({RESEARCHER})
  public Response uploadIrbDocument (
      @Auth AuthUser authUser,
      @PathParam("referenceId") String referenceId,
      @FormDataParam("file") InputStream uploadInputStream,
      @FormDataParam("file") FormDataContentDisposition fileDetail) {
    try {
      User user = findUserByEmail(authUser.getName());
      DataAccessRequest dar = getDarById(referenceId);
      checkAuthorizedUpdateUser(user, dar);
      DataAccessRequest updatedDar = updateDarWithDocumentContents(DarDocumentType.IRB, user, dar, uploadInputStream, fileDetail);
      return Response.ok(updatedDar.convertToSimplifiedDar()).build();
    } catch (Exception e) {
      return createExceptionResponse(e);
    }
  }

  @GET
  @Produces({MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON})
  @Path("/{referenceId}/collaborationDocument")
  @RolesAllowed({RESEARCHER})
  public Response getCollaborationDocument (
      @Auth AuthUser authUser,
      @PathParam("referenceId") String referenceId) {
    try {
      User user = findUserByEmail(authUser.getName());
      DataAccessRequest dar = getDarById(referenceId);
      checkAuthorizedUpdateUser(user, dar);
      if (Objects.nonNull(dar.getData().getCollaborationLetterLocation()) &&
        Objects.nonNull(dar.getData().getCollaborationLetterName())) {
        String blobIdName = dar.getData().getCollaborationLetterLocation();
        String fileName = dar.getData().getCollaborationLetterName();
        InputStream is = gcsService.getDocument(blobIdName);
        StreamingOutput stream = createStreamingOutput(is);
        return Response.ok(stream)
            .header("Content-Disposition", "attachment; filename=" + fileName)
            .build();
      }
      throw new NotFoundException();
    } catch (Exception e) {
      return createExceptionResponse(e);
    }
  }

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{referenceId}/collaborationDocument")
  @RolesAllowed({RESEARCHER})
  public Response uploadCollaborationDocument (
      @Auth AuthUser authUser,
      @PathParam("referenceId") String referenceId,
      @FormDataParam("file") InputStream uploadInputStream,
      @FormDataParam("file") FormDataContentDisposition fileDetail) {
    try {
      User user = findUserByEmail(authUser.getName());
      DataAccessRequest dar = getDarById(referenceId);
      checkAuthorizedUpdateUser(user, dar);
      DataAccessRequest updatedDar = updateDarWithDocumentContents(DarDocumentType.COLLABORATION, user, dar, uploadInputStream, fileDetail);
      return Response.ok(updatedDar.convertToSimplifiedDar()).build();
    } catch (Exception e) {
      return createExceptionResponse(e);
    }
  }

  @DELETE
  @Path("/{referenceId}")
  @Produces("application/json")
  @RolesAllowed({ADMIN, RESEARCHER})
  public Response deleteDar(@Auth AuthUser authUser, @PathParam("referenceId") String referenceId) {
    validateAuthedRoleUser(Collections.singletonList(UserRoles.ADMIN), authUser, referenceId);
    try {
      dataAccessRequestService.deleteByReferenceId(referenceId);
      return Response.ok().build();
    } catch (Exception e) {
      return createExceptionResponse(e);
    }
  }

  private User findUserByEmail(String email) {
    User user = userService.findUserByEmail(email);
    if (user == null) {
      throw new NotFoundException("Unable to find User with the provided email: " + email);
    }
    return user;
  }

  private DataAccessRequest populateDarFromJsonString(User user, String json) {
    DataAccessRequest newDar = new DataAccessRequest();
    DataAccessRequestData data = DataAccessRequestData.fromString(json);
    if (Objects.isNull(data)) {
      data = new DataAccessRequestData();
    }
    // When posting a submitted dar, there are two cases:
    // 1. those that existed previously as a draft dar
    // 2. those that are brand new
    // Validate the provided referenceId with the authenticated user and draft status
    // Those that do not validate are considered a brand new dar
    if (Objects.nonNull(data.getReferenceId())) {
      DataAccessRequest existingDar =
          dataAccessRequestService.findByReferenceId(data.getReferenceId());
      if (Objects.nonNull(existingDar)
          && existingDar.getUserId().equals(user.getDacUserId())
          && existingDar.getDraft()) {
        newDar.setReferenceId(data.getReferenceId());
      } else {
        String referenceId = UUID.randomUUID().toString();
        newDar.setReferenceId(referenceId);
        data.setReferenceId(referenceId);
      }
    } else {
      String referenceId = UUID.randomUUID().toString();
      newDar.setReferenceId(referenceId);
      data.setReferenceId(referenceId);
    }
    newDar.setData(data);
    return newDar;
  }

  private void checkAuthorizedUpdateUser(User user, DataAccessRequest dar) {
    if (!user.getDacUserId().equals(dar.getUserId())) {
      throw new ForbiddenException("User not authorized to update this Data Access Request");
    }
  }

  private DataAccessRequest updateDarWithDocumentContents(
      DarDocumentType type,
      User user,
      DataAccessRequest dar,
      InputStream uploadInputStream,
      FormDataContentDisposition fileDetail) throws IOException {
    validateFileDetails(fileDetail);
    String fileName = fileDetail.getFileName();
    String blobFileName =  UUID.randomUUID().toString();
    BlobId blobId = gcsService.storeDocument(uploadInputStream, fileDetail.getType(), blobFileName);
    switch (type) {
      case IRB:
        // Delete the current document if it exists
        if (Objects.nonNull(dar.getData().getIrbDocumentLocation())) {
          deleteDarDocument(dar, dar.getData().getIrbDocumentLocation());
        }
        dar.getData().setIrbDocumentLocation(blobId.getName());
        dar.getData().setIrbDocumentName(fileName);
        break;
      case COLLABORATION:
        // Delete the current document if it exists
        if (Objects.nonNull(dar.getData().getCollaborationLetterLocation())) {
          deleteDarDocument(dar, dar.getData().getCollaborationLetterLocation());
        }
        dar.getData().setCollaborationLetterLocation(blobId.getName());
        dar.getData().setCollaborationLetterName(fileName);
        break;
      default:
        break;
    }
    return dataAccessRequestService.updateByReferenceIdVersion2(user, dar);
  }

  private void deleteDarDocument(DataAccessRequest dar, String blobIdName) {
    try {
      gcsService.deleteDocument(blobIdName);
    } catch (Exception e) {
      String message = String.format("Unable to delete document for DAR ID: %s; dar document location: %s", dar.getReferenceId(), blobIdName);
      logger.warn(message);
    }
  }

  private DataAccessRequest getDarById(String referenceId) {
    DataAccessRequest dar = dataAccessRequestService.findByReferenceId(referenceId);
    if (Objects.isNull(dar)) {
      throw new NotFoundException();
    }
    return dar;
  }

  /**
   * Custom handler for validating that a user can access a DAR. User will have access if ANY
   * of these conditions are met:
   *      If the DAR create user is the same as the Auth User, then the user can access the resource.
   *      If the user has any of the roles in allowableRoles, then the user can access the resource.
   * In practice, pass in allowableRoles for users that are not the create user (i.e. Admin) so
   * they can also have access to the DAR.
   *
   * @param allowableRoles List of roles that would allow the user to access the resource
   * @param authUser The AuthUser
   * @param referenceId The referenceId of the resource.
   */
  private void validateAuthedRoleUser(final List<UserRoles> allowableRoles, AuthUser authUser, String referenceId) {
    DataAccessRequest dataAccessRequest = getDarById(referenceId);
    User user = findUserByEmail(authUser.getName());
    if (Objects.nonNull(dataAccessRequest.getUserId()) && dataAccessRequest.getUserId() > 0) {
      super.validateAuthedRoleUser(allowableRoles, user, dataAccessRequest.getUserId());
    } else {
      logger.warn("DataAccessRequest '" + referenceId + "' has an invalid userId" );
      super.validateAuthedRoleUser(allowableRoles, user, dataAccessRequest.getUserId());
    }
  }
}
