package org.broadinstitute.consent.http.service;

import com.google.common.collect.Collections2;
import com.mongodb.BasicDBObject;
import com.mongodb.Block;
import com.mongodb.client.FindIterable;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.broadinstitute.consent.http.db.ConsentDAO;
import org.broadinstitute.consent.http.db.ConsentMapper;
import org.broadinstitute.consent.http.db.ElectionDAO;
import org.broadinstitute.consent.http.db.mongo.MongoConsentDB;
import org.broadinstitute.consent.http.enumeration.ElectionStatus;
import org.broadinstitute.consent.http.enumeration.ElectionType;
import org.broadinstitute.consent.http.models.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation class for ConsentAPI on top of ConsentDAO database support.
 */
public class DatabaseConsentAPI extends AbstractConsentAPI {

    private ConsentDAO consentDAO;
    private ElectionDAO electionDAO;
    private DBI jdbi;
    private Logger logger;
    private final String UN_REVIEWED = "un-reviewed";
    private MongoConsentDB mongo;

    /**
     * Initialize the singleton API instance using the provided DAO.  This method should only be called once
     * during application initialization (from the run() method).  If called a second time it will throw an
     * IllegalStateException.
     * Note that this method is not synchronized, as it is not intended to be called more than once.
     *
     * @param dao The Data Access Object instance that the API should use to read/write data.
     */

    public static void initInstance(DBI jdbi, ConsentDAO dao , ElectionDAO electionDAO , MongoConsentDB mongo) {
        ConsentAPIHolder.setInstance(new DatabaseConsentAPI(jdbi, dao, electionDAO, mongo));
    }

    /**
     * The constructor is private to force use of the factory methods and enforce the singleton pattern.
     *
     * @param dao The Data Access Object used to read/write data.
     */
    private DatabaseConsentAPI(DBI jdbi, ConsentDAO dao , ElectionDAO electionDAO, MongoConsentDB mongo) {
        this.jdbi = jdbi;
        this.consentDAO = dao;
        this.electionDAO = electionDAO;
        this.mongo = mongo;
        this.logger = Logger.getLogger("DatabaseConsentAPI");

    }

    // Consent Methods

    @Override
    public Consent create(Consent rec) {
        String id;
        if (StringUtils.isNotEmpty(rec.consentId)){
            id = rec.consentId;
        }else{
            id = UUID.randomUUID().toString();
        }
        Date createDate = new Date();
        consentDAO.insertConsent(id, rec.requiresManualReview, rec.useRestriction.toString(), rec.getDataUseLetter(), rec.name, rec.dulName, createDate, createDate , rec.getTranslatedUseRestriction());
        return consentDAO.findConsentById(id);
    }


    @Override
    public Consent retrieve(String id) throws UnknownIdentifierException {
        return consentDAO.findConsentById(id);
    }

    @Override
    public Collection<Consent> findConsentsByAssociationType(String associationType) {
        return consentDAO.findConsentsByAssociationType(associationType);
    }

    @Override
    public Collection<Consent> retrieve(List<String> ids) {
        List<Consent> consents;
        try (Handle h = jdbi.open()) {
            consents = h.createQuery("select * from consents where consentId in (" + getInClauseStrings(ids) + ") and active=true").
                    map(new ConsentMapper()).
                    list();
        }
        return consents;
    }

    private String getInClauseStrings(Collection<String> strings) {
        Collection<String> quotedIds = Collections2.transform(strings, (String input) -> "'" + input + "'");
        return StringUtils.join(quotedIds, ",");
    }


    @Override
    public void update(String id, Consent rec) throws UnknownIdentifierException {
        rec = updateConsentDates(rec);
        consentDAO.updateConsent(id, rec.getRequiresManualReview(), rec.getUseRestriction().toString(), rec.getDataUseLetter(), rec.getName(), rec.getDulName(), rec.getLastUpdate(), rec.getSortDate(), rec.getTranslatedUseRestriction());
    }

    @Override
    public void delete(String id) throws  IllegalArgumentException {

            List<Election> elections = electionDAO.findElectionsByReferenceId(id);
            if(elections.isEmpty()){
                    consentDAO.deleteConsent(id);
                    consentDAO.deleteAllAssociationsForConsent(id);
                }else
                  throw new IllegalArgumentException();
             }

    @Override
    public void logicalDelete(String id) throws UnknownIdentifierException {
        consentDAO.logicalDeleteConsent(id);
    }

    // ConsentAssociation methods

    // POST /consent/:consentid/association <body>=List<ConsentAssociation>
    // Create new associations for a consent.  For each ConsentAssociation specified, remove the previous
    // association and create the new one.
    @Override
    public List<ConsentAssociation> createAssociation(String consentId, List<ConsentAssociation> new_associations) {
        logger.trace(String.format("createAssociation consentId='%s' %d associations supplied",
                consentId, new_associations.size()));
        checkConsentExists(consentId);
        validateAssociations(new_associations);

        for (ConsentAssociation association : new_associations) {
            logger.debug(String.format("CreateAssociation, adding associations for '%s', %d ids supplied",
                    association.getAssociationType(), association.getElements().size()));
            consentDAO.begin();
            consentDAO.deleteAllAssociationsForType(consentId, association.getAssociationType());
            consentDAO.insertAssociations(consentId, association.getAssociationType(), association.getElements());
            consentDAO.commit();
        }
        return getAllAssociationsForConsent(consentId);
    }

    // PUT /consent/:consentid/association <body>=List<ConsentAssociaiton>
    // Update associations for a consent by adding new associations.  For each ConsentAssociation specified, all
    // the objects specified are added as consent associations.
    @Override
    public List<ConsentAssociation> updateAssociation(String consentId, List<ConsentAssociation> new_associations) {
        logger.trace(String.format("updateAssociation consentId='%s' associations(%d)= '%s'",
                consentId, new_associations.size(), new_associations.toString()));
        checkConsentExists(consentId);
        validateAssociations(new_associations);

        // Loop over all the ConsentAssociations sent in the body.
        for (ConsentAssociation association : new_associations) {
            String atype = association.getAssociationType();
            List<String> new_ids = association.getElements();
            logger.debug(String.format("updateAssociation, adding associations for '%s', ids(cnt=%d) are '%s'",
                    atype, new_ids.size(), new_ids.toString()));
            // First retrieve the existing associations for that type.
            List<String> old_ids = consentDAO.findAssociationsByType(consentId, atype);
            // Remove any objectId's that already exist
            new_ids.removeAll(old_ids);
            // and add the new associations
            consentDAO.insertAssociations(consentId, atype, new_ids);
        }

        return getAllAssociationsForConsent(consentId);
    }

    @Override
    public List<ConsentAssociation> getAssociation(String consentId, String associationType, String objectId) {
        logger.trace(String.format("getAssociation consentId='%s' associationType='%s', objectId='%s'",
                consentId, associationType, objectId));

        checkConsentExists(consentId);
        List<ConsentAssociation> result;
        if (associationType == null)
            result = getAllAssociationsForConsent(consentId);
        else {
            result = new ArrayList<>();
            List<String> id_list;
            if (objectId == null)
                id_list = consentDAO.findAssociationsByType(consentId, associationType);
            else { // both associationType and objectId specified
                id_list = new ArrayList<>();
                if (consentDAO.findAssociationByTypeAndId(consentId, associationType, objectId) != null)
                    id_list.add(objectId);
            }
            result.add(new ConsentAssociation(associationType, id_list));
        }
        return result;
    }

    @Override
    public List<ConsentAssociation> deleteAssociation(String consentId, String associationType, String objectId) {
        logger.trace(String.format("deleteAssociation consentId='%s' associationType='%s', objectId='%s'",
                consentId, (associationType == null ? "<null>" : associationType),
                (objectId == null ? "<null>" : objectId)));

        checkConsentExists(consentId);
        if (associationType == null)
            consentDAO.deleteAllAssociationsForConsent(consentId);
        else if (objectId == null)
            consentDAO.deleteAllAssociationsForType(consentId, associationType);
        else { // both associationType and objectId specified
            if (consentDAO.findAssociationByTypeAndId(consentId, associationType, objectId) == null)
                throw new NotFoundException();
            consentDAO.deleteOneAssociation(consentId, associationType, objectId);
        }
        return getAllAssociationsForConsent(consentId);
    }

    @Override
    public List<String> getConsentsForAssociation(UriInfo uriInfo, String associationType, String objectId) {
        // We get a list of consentId's (UUID's) from the database, and turn them into URIs
        // <base-service-url>/consent/{id}
        List<String> consent_ids = consentDAO.findConsentsForAssociation(associationType, objectId);
        List<String> consent_uris = new ArrayList<>(consent_ids.size());
        for (String consentId : consent_ids) {
            UriBuilder ub = uriInfo.getBaseUriBuilder();
            URI consentUri = ub.path("consent").path(consentId).build();
            consent_uris.add((consentUri.getPath()));
        }
        logger.debug(String.format("getConsentsForAssociation(%s,%s) returning '%s'", associationType, objectId, consent_uris.toString()));
        return consent_uris;
    }

    @Override
    public Consent getConsentFromDatasetID(String datasetId){
        return consentDAO.findConsentFromDatasetID(datasetId);
    }

    @Override
    public Collection<Consent> getConsentsFromDatasetIDs(List<String> datasetIds) {
        return consentDAO.findConsentsFromDatasetIDs(datasetIds);
    }

    @Override
    public Set<ConsentDataSet> getConsentIdAndDataSets(List<String> datasetIds){
        return consentDAO.getConsentIdAndDataSets(datasetIds);
    }

    @Override
    public Consent updateConsentDul(String consentId, String dataUseLetter, String dulName) throws UnknownIdentifierException {
        Consent consent = retrieve(consentId);
        consent.setDulName(dulName);
        consent.setDataUseLetter(dataUseLetter);
        update(consentId, updateConsentDates(consent));
        return retrieve(consentId);
    }

    @Override
    public String getConsentDulUrl(String consentId) throws UnknownIdentifierException {
        Consent consent = retrieve(consentId);
        return consent.getDataUseLetter();
    }

    @Override
    public Consent deleteConsentDul(String consentId) throws UnknownIdentifierException {
        Consent consent = retrieve(consentId);
        consent.setDataUseLetter("");
        update(consentId, updateConsentDates(consent));
        return retrieve(consentId);
    }

    // Helper methods for Consent Associations
    //
    // Check that the list of ConsentAssociations given as an agrument is valid, and throw BadRequestException
    // if not.  The only error checked for is duplicate associationType.
    private void validateAssociations(List<ConsentAssociation> assoc_list) {
        if (assoc_list.size() > 1) {
            Set<String> atype_list = new HashSet<>();
            for (ConsentAssociation assoc : assoc_list) {
                if (atype_list.contains(assoc.getAssociationType()))
                    throw new WebApplicationException(Response.Status.BAD_REQUEST);
                atype_list.add(assoc.getAssociationType());
            }
        }
    }

    // Check that the specified Consent resource exists, or throw NotFoundException.
    private void checkConsentExists(String consentId) {
        String ck_id = consentDAO.checkConsentbyId(consentId);
        logger.debug(String.format("CreateAssocition, checkConsentbyId returned '%s'", (ck_id == null ? "<null>" : ck_id)));
        if (ck_id == null)
            throw new NotFoundException(String.format("Consent with id '%s' not found", consentId));
    }

    // Get the updated list of all the associations for a Consent Resource, to return as a result.
    private List<ConsentAssociation> getAllAssociationsForConsent(String consentId) {
        List<ConsentAssociation> assoc_list = new ArrayList<>();
        List<String> type_list = consentDAO.findAssociationTypesForConsent(consentId);
        logger.debug(String.format("getAllAssociationsForConsent consentId='%s', types='%s'", consentId, type_list.toString()));
        for (String atype : type_list) {
            List<String> id_list = consentDAO.findAssociationsByType(consentId, atype);
            logger.debug(String.format("getAllAssociationsForConsent adding %d ids to type '%s'", id_list.size(), atype));
            ConsentAssociation next_assoc = new ConsentAssociation(atype, id_list);
            assoc_list.add(next_assoc);
        }
        logger.debug(String.format("getAllAssociationsForConsent - returning '%s'", assoc_list.toString()));
        return assoc_list;
    }

    @Override
    public List<ConsentManage> describeConsentManage() {
        List<ConsentManage> consentManageList = new ArrayList<>();
        consentManageList.addAll(collectUnreviewedConsents(consentDAO.findUnreviewedConsents(), UN_REVIEWED));
        consentManageList.addAll(consentDAO.findConsentManageByStatus(ElectionStatus.OPEN.getValue()));
        consentManageList.addAll(consentDAO.findConsentManageByStatus(ElectionStatus.CANCELED.getValue()));
        consentManageList.addAll(consentDAO.findConsentManageByStatus(ElectionStatus.CLOSED.getValue()));
        consentManageList.sort((c1, c2) -> c2.getSortDate().compareTo(c1.getSortDate()));
        String electionTypeId = electionDAO.findElectionTypeByType(ElectionType.DATA_ACCESS.getValue());
        List<Election> openElections = electionDAO.findElectionsByTypeAndStatus(electionTypeId, ElectionStatus.OPEN.getValue());
        if(!openElections.isEmpty()) {
            List<String> referenceIds = openElections.stream().map(sc -> sc.getReferenceId()).collect(Collectors.toList());
            ObjectId[] objarray = new ObjectId[referenceIds.size()];
            for (int i = 0; i < referenceIds.size(); i++)
                objarray[i] = new ObjectId(referenceIds.get(i));
            BasicDBObject in = new BasicDBObject("$in", objarray);
            BasicDBObject q = new BasicDBObject("_id", in);
            FindIterable<Document> dataAccessRequests = mongo.getDataAccessRequestCollection().find(q);
            List<String> datasetNames = new ArrayList<>();
            dataAccessRequests.forEach((Block<Document>) dar -> {
                List<String> dataSets = dar.get("datasetId", List.class);
                datasetNames.addAll(dataSets);
            });
            List<String> objectIds = consentDAO.getAssociationsConsentIdfromObjectIds(datasetNames);
            for (ConsentManage consentManage : consentManageList) {
                if (objectIds.stream().anyMatch(cm -> cm.equals(consentManage.getConsentId()))) {
                    consentManage.setEditable(false);
                } else {
                    consentManage.setEditable(true);
                }
            }
        }
        return consentManageList;
    }

    @Override
    public Integer getUnReviewedConsents(){
        Integer unreviewedCases = 0;
        List<Consent> consents = consentDAO.findUnreviewedConsents();
        if(CollectionUtils.isNotEmpty(consents)){
            unreviewedCases = consents.size();
        }
        return unreviewedCases;
    }

    private List<ConsentManage> collectUnreviewedConsents(List<Consent> consents, String status) {
        List<ConsentManage> consentManageList = consents.stream().map(ConsentManage::new).collect(Collectors.toList());
        consentManageList.forEach(c -> c.setElectionStatus(status));
        consentManageList.forEach(c -> c.setEditable(true));
        return consentManageList;
    }

    private Consent updateConsentDates(Consent c){
        Timestamp updateDate = new Timestamp(new Date().getTime());
        c.setLastUpdate(updateDate);
        c.setSortDate(updateDate);
        return c;
    }

}