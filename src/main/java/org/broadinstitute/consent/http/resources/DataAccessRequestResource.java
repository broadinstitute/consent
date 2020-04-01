package org.broadinstitute.consent.http.resources;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import freemarker.template.TemplateException;
import io.dropwizard.auth.Auth;
import org.apache.commons.collections.CollectionUtils;
import org.broadinstitute.consent.http.cloudstore.GCSStore;
import org.broadinstitute.consent.http.enumeration.ResearcherFields;
import org.broadinstitute.consent.http.models.AuthUser;
import org.broadinstitute.consent.http.models.Consent;
import org.broadinstitute.consent.http.models.DACUser;
import org.broadinstitute.consent.http.models.DataAccessRequest;
import org.broadinstitute.consent.http.models.DataAccessRequestData;
import org.broadinstitute.consent.http.models.DataAccessRequestManage;
import org.broadinstitute.consent.http.models.DataSet;
import org.broadinstitute.consent.http.models.darsummary.DARModalDetailsDTO;
import org.broadinstitute.consent.http.models.dto.DataSetDTO;
import org.broadinstitute.consent.http.models.dto.Error;
import org.broadinstitute.consent.http.models.grammar.UseRestriction;
import org.broadinstitute.consent.http.service.AbstractConsentAPI;
import org.broadinstitute.consent.http.service.AbstractDataAccessRequestAPI;
import org.broadinstitute.consent.http.service.AbstractDataSetAPI;
import org.broadinstitute.consent.http.service.AbstractElectionAPI;
import org.broadinstitute.consent.http.service.AbstractEmailNotifierAPI;
import org.broadinstitute.consent.http.service.AbstractMatchProcessAPI;
import org.broadinstitute.consent.http.service.AbstractTranslateService;
import org.broadinstitute.consent.http.service.ConsentAPI;
import org.broadinstitute.consent.http.service.DataAccessRequestAPI;
import org.broadinstitute.consent.http.service.DataAccessRequestService;
import org.broadinstitute.consent.http.service.DataSetAPI;
import org.broadinstitute.consent.http.service.ElectionAPI;
import org.broadinstitute.consent.http.service.EmailNotifierAPI;
import org.broadinstitute.consent.http.service.MatchProcessAPI;
import org.broadinstitute.consent.http.service.TranslateService;
import org.broadinstitute.consent.http.service.UserService;
import org.broadinstitute.consent.http.service.users.AbstractDACUserAPI;
import org.broadinstitute.consent.http.service.users.DACUserAPI;
import org.broadinstitute.consent.http.service.validate.AbstractUseRestrictionValidatorAPI;
import org.broadinstitute.consent.http.service.validate.UseRestrictionValidatorAPI;
import org.broadinstitute.consent.http.util.DarConstants;
import org.broadinstitute.consent.http.util.DarUtil;
import org.bson.Document;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.mail.MessagingException;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
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
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Path("api/dar")
public class DataAccessRequestResource extends Resource {

    private static final Logger logger = Logger.getLogger(DataAccessRequestResource.class.getName());
    private final DataAccessRequestService dataAccessRequestService;
    private final DataAccessRequestAPI dataAccessRequestAPI;
    private final ConsentAPI consentAPI;
    private final MatchProcessAPI matchProcessAPI;
    private final EmailNotifierAPI emailApi;
    private final TranslateService translateService = AbstractTranslateService.getInstance();
    private final DataSetAPI dataSetAPI = AbstractDataSetAPI.getInstance();
    private final UseRestrictionValidatorAPI useRestrictionValidatorAPI;
    private final DACUserAPI dacUserAPI;
    private final ElectionAPI electionAPI;
    private final GCSStore store;
    private final UserService userService;

    @Inject
    public DataAccessRequestResource(DataAccessRequestService dataAccessRequestService, GCSStore store, UserService userService) {
        this.dataAccessRequestService = dataAccessRequestService;
        this.dataAccessRequestAPI = AbstractDataAccessRequestAPI.getInstance();
        this.consentAPI = AbstractConsentAPI.getInstance();
        this.matchProcessAPI = AbstractMatchProcessAPI.getInstance();
        this.emailApi = AbstractEmailNotifierAPI.getInstance();
        this.useRestrictionValidatorAPI = AbstractUseRestrictionValidatorAPI.getInstance();
        this.dacUserAPI = AbstractDACUserAPI.getInstance();
        this.electionAPI = AbstractElectionAPI.getInstance();
        this.store = store;
        this.userService = userService;
    }

    @POST
    @Consumes("application/json")
    @Produces("application/json")
    @RolesAllowed(RESEARCHER)
    public Response createDataAccessRequest(@Context UriInfo info, Document dar) {
        UseRestriction useRestriction;
        try {
            Boolean needsManualReview = DarUtil.requiresManualReview(dar);
            try {
                if (!needsManualReview) {
                    useRestriction = dataAccessRequestAPI.createStructuredResearchPurpose(dar);
                    dar.append(DarConstants.RESTRICTION, Document.parse(useRestriction.toString()));
                    useRestrictionValidatorAPI.validateUseRestriction(useRestriction.toString());
                    dar.append(DarConstants.VALID_RESTRICTION, true);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error creating use restriction for data access request " + dar.toJson(), e);
            }
            dar.append(DarConstants.TRANSLATED_RESTRICTION, translateService.generateStructuredTranslatedRestriction(dar, needsManualReview));
            dar.append(DarConstants.SORT_DATE, new Date().getTime());
            List<Document> results = dataAccessRequestAPI.createDataAccessRequest(dar);
            URI uri = info.getRequestUriBuilder().build();
            for (Document r : results) {
                List<Integer> datasetIds = DarUtil.getIntegerList(r, DarConstants.DATASET_ID);
                matchProcessAPI.processMatchesForPurpose(r.getString(DarConstants.REFERENCE_ID));
                emailApi.sendNewDARRequestMessage(r.getString(DarConstants.DAR_CODE), datasetIds);
            }
            return Response.created(uri).build();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error creating data access request ", e);
            store.deleteStorageDocument(dar.getString(ResearcherFields.URL_DAA.getValue()));
            return createExceptionResponse(e);
        }
    }

    @PUT
    @Consumes("application/json")
    @Produces("application/json")
    @Path("/{id}")
    @RolesAllowed(RESEARCHER)
    public Response updateDataAccessRequest(Document dar, @PathParam("id") String id) {
        try {
            if (dar.containsKey(DarConstants.RESTRICTION)) {
                dar.remove(DarConstants.RESTRICTION);
            }
            Boolean needsManualReview = DarUtil.requiresManualReview(dar);
            if (!needsManualReview) {
                // generates research purpose, if needed, and store it on Document rus
                UseRestriction useRestriction = dataAccessRequestAPI.createStructuredResearchPurpose(dar);
                dar.append(DarConstants.RESTRICTION, Document.parse(useRestriction.toString()));
            }
            dar.append(DarConstants.TRANSLATED_RESTRICTION, translateService.generateStructuredTranslatedRestriction(dar, needsManualReview));
            dar = dataAccessRequestAPI.updateDataAccessRequest(dar, id);
            matchProcessAPI.processMatchesForPurpose(dar.getString(DarConstants.REFERENCE_ID));
            return Response.ok().entity(dataAccessRequestAPI.updateDataAccessRequest(dar, id)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }

    }

    @GET
    @Produces("application/json")
    @Path("/modalSummary/{id}")
    @PermitAll
    public Response getDataAcessRequestModalSummary(@PathParam("id") String id) {
        Document dar = dataAccessRequestAPI.describeDataAccessRequestById(id);
        Integer userId = obtainUserId(dar);
        DACUser user = null;
        try {
            user = dacUserAPI.describeDACUserById(userId);
        } catch (NotFoundException e) {
            logger.severe("Unable to find userId: " + userId + " for data access request id: " + id);
        }
        DARModalDetailsDTO detailsDTO = dataAccessRequestAPI.DARModalDetailsDTOBuilder(dar, user, electionAPI);
        return Response.ok().entity(detailsDTO).build();
    }

    @GET
    @Produces("application/json")
    @Path("/invalid")
    @RolesAllowed(ADMIN)
    public Response getInvalidDataAccessRequest() {
        try {
            return Response.status(Response.Status.OK).entity(dataAccessRequestAPI.getInvalidDataAccessRequest()).build();
        } catch (Exception e) {
            return createExceptionResponse(e);
        }

    }

    @GET
    @Produces("application/json")
    @PermitAll
    public Response describeDataAccessRequests(@Auth AuthUser authUser) {
        List<Document> documents = dataAccessRequestService.describeDataAccessRequests(authUser);
        return Response.ok().entity(documents).build();
    }

    /**
     * Temporary admin-only endpoint for mongo->postgres DAR conversion
     *
     * @param authUser AuthUser
     * @return List of all DataAccessRequests in Mongo
     */
    @GET
    @Path("/migrate/mongo")
    @Produces("application/json")
    @RolesAllowed(ADMIN)
    public Response getAllMongoDataAccessRequests(@Auth AuthUser authUser) {
        Map<String, Document> map = dataAccessRequestService.getAllMongoDataAccessRequests().
                stream().
                collect(Collectors.toMap(d -> d.get(DarConstants.ID).toString(), d -> d));
        return Response.ok().entity(map).build();
    }

    /**
     * Temporary admin-only endpoint for mongo->postgres DAR conversion
     *
     * @param authUser AuthUser
     * @return List of all DataAccessRequests in Postgres
     */
    @GET
    @Path("/migrate/postgres")
    @Produces("application/json")
    @RolesAllowed(ADMIN)
    public Response getAllPostgresDataAccessRequests(@Auth AuthUser authUser) {
        List<DataAccessRequest> data = dataAccessRequestService.getAllPostgresDataAccessRequests();
        return Response.ok().entity(data).build();
    }

    /**
     * Temporary admin-only endpoint for mongo->postgres DAR conversion
     *
     * @param authUser AuthUser
     * @return Converted DataAccessRequest
     */
    @POST
    @Path("migrate/{id}")
    @Produces("application/json")
    @RolesAllowed(ADMIN)
    public Response convertDataAccessRequest(@Auth AuthUser authUser, @PathParam("id") String id, String json) {
        DataAccessRequestData data = DataAccessRequestData.fromString(json);
        if (data.getCreateDate() == null) {
            // Original create date was inferred from mongo ObjectId.timestamp
            Gson gson = new Gson();
            JsonObject obj = gson.fromJson(json, JsonObject.class);
            long createDate = new Date().getTime();
            if (obj.has("_id")) {
                JsonObject idObject = obj.getAsJsonObject("_id");
                if (idObject.has("timestamp")) {
                    long timestamp = idObject.get("timestamp").getAsLong();
                    createDate = timestamp * 1000; // Fix Mongo's timestamp
                }
            }
            data.setCreateDate(createDate);
        }
        DataAccessRequest dar = dataAccessRequestService.findByReferenceId(id);
        if (dar == null) {
            dar = dataAccessRequestService.insertDataAccessRequest(id, data);
        }
        return Response.ok().entity(dar).build();
    }

    @GET
    @Path("/{id}")
    @Produces("application/json")
    @PermitAll
    public Response describe(@PathParam("id") String id) {
        try {
            Document document = dataAccessRequestService.getDataAccessRequestByReferenceIdAsDocument(id);
            if (document != null) {
                return Response.status(Response.Status.OK).entity(document).build();
            }
            return Response
                    .status(Response.Status.NOT_FOUND)
                    .entity(new Error(
                                    "Unable to find Data Access Request with id: " + id,
                                    Response.Status.NOT_FOUND.getStatusCode()))
                    .build();
        } catch (Exception e) {
            return createExceptionResponse(e);
        }
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    @RolesAllowed({RESEARCHER, ADMIN})
    public Response delete(@PathParam("id") String id) {
        try {
            dataAccessRequestAPI.deleteDataAccessRequestById(id);
            matchProcessAPI.removeMatchesForPurpose(id);
            return Response.status(Response.Status.OK).entity("Research Purpose was deleted").build();
        } catch (Exception e) {
            return createExceptionResponse(e);
        }
    }

    @GET
    @Path("/find/{id}")
    @Produces("application/json")
    @PermitAll
    public Document describeSpecificFields(@PathParam("id") String id, @QueryParam("fields") List<String> fields) {
        if (CollectionUtils.isNotEmpty(fields)) {
            List<String> fieldValues = Arrays.asList(fields.get(0).split(","));
            return dataAccessRequestAPI.describeDataAccessRequestFieldsById(id, fieldValues);
        } else {
            return dataAccessRequestAPI.describeDataAccessRequestById(id);
        }
    }

    /**
     * Note that this method assumes a single consent for a DAR. The UI doesn't curently handle the
     * case where there are multiple datasets associated to a DAR.
     * See https://broadinstitute.atlassian.net/browse/BTRX-717 to handle that condition.
     *
     * @param id The Data Access Request ID
     * @return consent The consent associated to the first dataset id the DAR refers to.
     */
    @GET
    @Path("/find/{id}/consent")
    @Produces("application/json")
    @PermitAll
    public Consent describeConsentForDAR(@PathParam("id") String id) {
        Optional<Integer> dataSetId = getDatasetIdForDarId(id);
        Consent c;
        if (dataSetId.isPresent()) {
            c = consentAPI.getConsentFromDatasetID(dataSetId.get());
            if (c == null) {
                throw new NotFoundException("Unable to find the consent related to the datasetId present in the DAR.");
            }
        } else {
            throw new NotFoundException("Unable to find the datasetId related to the DAR.");
        }
        return c;
    }


    @GET
    @Produces("application/json")
    @Path("/manage")
    @RolesAllowed({ADMIN, CHAIRPERSON, RESEARCHER})
    public Response describeManageDataAccessRequests(@QueryParam("userId") Integer userId, @Auth AuthUser authUser) {
        // If a user id is provided, ensure that is the current user.
        if (userId != null) {
            DACUser user = userService.findUserByEmail(authUser.getName());
            if (!user.getDacUserId().equals(userId)) {
                throw new BadRequestException("Unable to query for other users' information.");
            }
        }
        List<DataAccessRequestManage> dars = dataAccessRequestService.describeDataAccessRequestManage(userId, authUser);
        return Response.ok().entity(dars).build();
    }

    @GET
    @Path("cases/unreviewed")
    @Produces("application/json")
    @RolesAllowed(ADMIN)
    public Response getTotalUnReviewedDAR(@Auth AuthUser authUser) {
        int count = dataAccessRequestService.getTotalUnReviewedDars(authUser);
        UnreviewedCases entity = new UnreviewedCases(count);
        return Response.ok().entity(entity).build();
    }

    // Partial Data Access Requests Methods

    @GET
    @Produces("application/json")
    @Path("/partials")
    @RolesAllowed(RESEARCHER)
    public List<Document> describePartialDataAccessRequests() {
        return dataAccessRequestAPI.describePartialDataAccessRequests();
    }

    @POST
    @Consumes("application/json")
    @Produces("application/json")
    @Path("/partial")
    @RolesAllowed(RESEARCHER)
    public Response createPartialDataAccessRequest(@Context UriInfo info, Document dar) {
        URI uri;
        Document result = null;
        if ((dar.size() == 1 && dar.containsKey("userId")) || (dar.size() == 0)) {
            return Response.status(Response.Status.BAD_REQUEST).entity(new Error("The Data Access Request is empty. Please, complete the form with the information you want to save.", Response.Status.BAD_REQUEST.getStatusCode())).build();
        }
        try {
            result = savePartialDarRequest(dar);
            uri = info.getRequestUriBuilder().path("{id}").build(result.getString(DarConstants.REFERENCE_ID));
            return Response.created(uri).entity(result).build();
        } catch (Exception e) {
            dataAccessRequestAPI.deleteDataAccessRequest(result);
            return createExceptionResponse(e);
        }
    }

    @POST
    @Consumes("application/json")
    @Produces("application/json")
    @Path("/partial/datasetCatalog")
    @RolesAllowed(RESEARCHER)
    /*
     * Note: Run this endpoint only once, in order to apply datasets correspondent alias Id
     * in MySql and replace objectId to datasetId in Mongodb
     */
    public Response createPartialDataAccessRequestFromCatalog(@QueryParam("userId") Integer userId, List<Integer> datasetIds) {
        Document dar = new Document();
        Collection<DataSetDTO> dataSets = dataSetAPI.describeDataSetsByReceiveOrder(datasetIds);
        List<String> dataSetNames = dataSets.stream().map(dataset -> dataset.getPropertyValue("Dataset Name")).collect(Collectors.toList());
        dar.append(DarConstants.USER_ID, userId);
        try {
            List<Map<String, String>> datasets = new ArrayList<>();
            for (String datasetName : dataSetNames) {
                List<Map<String, String>> ds = dataSetAPI.getCompleteDataSet(datasetName);
                datasets.add(ds.get(0));
            }
            dar.append(DarConstants.DATASET_ID, datasets);
            return Response.ok().entity(dar).build();
        } catch (Exception e) {
            logger.log(Level.SEVERE, " while fetching dataset details to export to DAR formulary. UserId: " + userId + ", datasets: " + datasetIds.toString() + ". Cause: " + e.getLocalizedMessage());
            return createExceptionResponse(e);
        }
    }


    @PUT
    @Consumes("application/json")
    @Produces("application/json")
    @Path("/partial")
    @RolesAllowed(RESEARCHER)
    public Response updatePartialDataAccessRequest(@Context UriInfo info, Document dar) {
        try {
            dar = dataAccessRequestAPI.updatePartialDataAccessRequest(dar);
            return Response.ok().entity(dar).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @GET
    @Produces("application/json")
    @Path("/partial/{id}")
    @RolesAllowed(RESEARCHER)
    public Document describePartialDar(@PathParam("id") String id) {
        return dataAccessRequestAPI.describePartialDataAccessRequestById(id);
    }


    @DELETE
    @Produces("application/json")
    @Path("/partial/{id}")
    @RolesAllowed(RESEARCHER)
    public Response deletePartialDar(@PathParam("id") String id, @Context UriInfo info) {
        try {
            dataAccessRequestAPI.deletePartialDataAccessRequestById(id);
            return Response.ok().build();
        } catch (Exception e) {
            return createExceptionResponse(e);
        }
    }

    @GET
    @Produces("application/json")
    @Path("/partials/manage")
    @RolesAllowed(RESEARCHER)
    public Response describePartialManageDataAccessRequests(@QueryParam("userId") Integer userId) {
        return Response.ok().entity(dataAccessRequestAPI.describePartialDataAccessRequestManage(userId)).build();
    }


    @PUT
    @Consumes("application/json")
    @Produces("application/json")
    @Path("/cancel/{referenceId}")
    @RolesAllowed(RESEARCHER)
    public Response cancelDataAccessRequest(@PathParam("referenceId") String referenceId) {
        try {
            List<DACUser> usersToNotify = dataAccessRequestAPI.getUserEmailAndCancelElection(referenceId);
            Document dar = dataAccessRequestAPI.cancelDataAccessRequest(referenceId);
            if (CollectionUtils.isNotEmpty(usersToNotify)) {
                emailApi.sendCancelDARRequestMessage(usersToNotify, dar.getString(DarConstants.DAR_CODE));
            }
            return Response.ok().entity(dar).build();
        } catch (MessagingException | TemplateException | IOException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(new Error("The Data Access Request was cancelled but the DAC/Admin couldn't be notified. Contact Support. ", Response.Status.BAD_REQUEST.getStatusCode())).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new Error("Internal server error on delete. Please try again later. ", Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())).build();
        }
    }

    @GET
    @Consumes("application/json")
    @Produces("application/json")
    @Path("/hasUseRestriction/{referenceId}")
    @PermitAll
    public Response hasUseRestriction(@PathParam("referenceId") String referenceId) {
        try {
            return Response.ok("{\"hasUseRestriction\":" + dataAccessRequestAPI.hasUseRestriction(referenceId) + "}").build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new Error(e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())).build();
        }
    }

    @PUT
    @Consumes("application/json")
    @Produces("application/json")
    @Path("/restriction")
    @PermitAll
    public Response getUseRestrictionFromQuestions(Document dar) {
        try {
            Boolean needsManualReview = DarUtil.requiresManualReview(dar);
            if (!needsManualReview) {
                UseRestriction useRestriction = dataAccessRequestAPI.createStructuredResearchPurpose(dar);
                dar.append(DarConstants.RESTRICTION, Document.parse(useRestriction.toString()));
                return Response.ok(useRestriction).build();
            } else {
                return Response.ok("{\"useRestriction\":\"Manual Review\"}").build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new Error(e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())).build();
        }
    }

    private Document savePartialDarRequest(Document dar) throws Exception {
        dar.append(DarConstants.SORT_DATE, new Date().getTime());
        return dataAccessRequestAPI.createPartialDataAccessRequest(dar);
    }

    private Integer obtainUserId(Document dar) {
        try {
            return dar.getInteger("userId");
        } catch (Exception e) {
            return Integer.valueOf(dar.getString("userId"));
        }
    }

    /**
     * Data Access Requests have a `datasetId` that can refer to either a numeric id (newer model) or to
     * a string value pointing to the sample collection id (legacy model).
     *
     * @param id The DAR document id
     * @return Optional integer value of the referenced dataset.
     */
    private Optional<Integer> getDatasetIdForDarId(String id) {
        List datasetIdList = dataAccessRequestAPI.
                describeDataAccessRequestFieldsById(id, Collections.singletonList(DarConstants.DATASET_ID)).
                get("datasetId", List.class);
        if (datasetIdList == null || datasetIdList.isEmpty()) {
            return Optional.empty();
        } else {
            Object datasetId = datasetIdList.get(0);
            try {
                return Optional.of(Integer.valueOf(datasetId.toString()));
            } catch (NumberFormatException e) {
                DataSet dataset = dataSetAPI.findDataSetByObjectId(datasetId.toString());
                if (dataset != null) {
                    return Optional.of(dataset.getDataSetId());
                }
            }
        }
        return Optional.empty();
    }

    static class UnreviewedCases {
        @JsonProperty
        Integer darUnReviewedCases;

        UnreviewedCases(Integer darUnReviewedCases) {
            this.darUnReviewedCases = darUnReviewedCases;
        }
    }

}
