package org.broadinstitute.consent.http.service;

import org.broadinstitute.consent.http.models.DataAccessRequest;
import static org.broadinstitute.consent.http.resources.Resource.CHAIRPERSON;
import com.google.inject.Inject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.consent.http.db.ConsentDAO;
import org.broadinstitute.consent.http.db.DatasetDAO;
import org.broadinstitute.consent.http.db.ElectionDAO;
import org.broadinstitute.consent.http.db.MatchDAO;
import org.broadinstitute.consent.http.db.UserDAO;
import org.broadinstitute.consent.http.db.VoteDAO;
import org.broadinstitute.consent.http.enumeration.ElectionStatus;
import org.broadinstitute.consent.http.enumeration.ElectionType;
import org.broadinstitute.consent.http.enumeration.HeaderSummary;
import org.broadinstitute.consent.http.enumeration.VoteType;
import org.broadinstitute.consent.http.models.AccessRP;
import org.broadinstitute.consent.http.models.Association;
import org.broadinstitute.consent.http.models.Consent;
import org.broadinstitute.consent.http.models.DataSet;
import org.broadinstitute.consent.http.models.Election;
import org.broadinstitute.consent.http.models.Match;
import org.broadinstitute.consent.http.models.Summary;
import org.broadinstitute.consent.http.models.User;
import org.broadinstitute.consent.http.models.Vote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SummaryService {

    private final VoteDAO voteDAO;
    private final ElectionDAO electionDAO;
    private final UserDAO userDAO;
    private final ConsentDAO consentDAO;
    private final DatasetDAO datasetDAO;
    private final MatchDAO matchDAO;
    private final DataAccessRequestService dataAccessRequestService;
    private static final String SEPARATOR = "\t";
    private static final String TEXT_DELIMITER = "\"";
    private static final String END_OF_LINE = System.lineSeparator();
    private static final String MANUAL_REVIEW = "Manual Review";
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Inject
    public SummaryService(DataAccessRequestService dataAccessRequestService, VoteDAO dao,
        ElectionDAO electionDAO, UserDAO userDAO, ConsentDAO consentDAO, DatasetDAO datasetDAO,
        MatchDAO matchDAO) {
        this.dataAccessRequestService = dataAccessRequestService;
        this.voteDAO = dao;
        this.electionDAO = electionDAO;
        this.userDAO = userDAO;
        this.consentDAO = consentDAO;
        this.datasetDAO = datasetDAO;
        this.matchDAO = matchDAO;
    }

    public Summary describeConsentSummaryCases() {
        return getSummaryCases(ElectionType.TRANSLATE_DUL.getValue());
    }

    public Summary describeDataRequestSummaryCases(String electionType) {
        Summary summary;
        if(electionType.equals(ElectionType.DATA_ACCESS.getValue())){
            summary = getAccessSummaryCases(electionType);
        }else{
            summary = getSummaryCases(electionType);
        }
        return summary;
    }

    public List<Summary> describeMatchSummaryCases() {
        return getMatchSummaryCases();
    }

    private List<Summary> getMatchSummaryCases() {
        List<Summary> summaryList = new ArrayList<>();
        summaryList.add(createSummary(0, matchDAO.countMatchesByResult(Boolean.TRUE), matchDAO.countMatchesByResult(Boolean.FALSE)));

        List<Election> latestElections = electionDAO.findLastElectionsWithFinalVoteByType(ElectionType.DATA_ACCESS.getValue());
        List<Election> reviewedElections = null;
        if(!CollectionUtils.isEmpty(latestElections)){
            reviewedElections = latestElections.stream().filter(le -> le.getStatus().equals(ElectionStatus.CLOSED.getValue())).collect(Collectors.toList());
        }
        if( !CollectionUtils.isEmpty(reviewedElections)){
            List<Integer> electionIds = reviewedElections.stream().map(Election::getElectionId).collect(Collectors.toList());
            List<Vote> votes = voteDAO.findVotesByElectionIds(electionIds);

            List<Vote> agreementYesVotes = filterAgreementVotes(votes, Boolean.TRUE);
            List<Vote> agreementNoVotes = filterAgreementVotes(votes, Boolean.FALSE);

            if (CollectionUtils.isNotEmpty(agreementYesVotes) || CollectionUtils.isNotEmpty(agreementNoVotes)) {
                summaryList.add(createSummary(0, agreementYesVotes.size(), agreementNoVotes.size()));
            }
        }else{
            summaryList.add(createSummary(0,0,0));
        }
        return summaryList;
    }

    private List<Vote> filterAgreementVotes(List<Vote> votes, Boolean desiredValue) {
        return votes.stream().filter(
                v -> v.getType().equals(VoteType.AGREEMENT.getValue()) && v.getVote() != null && v.getVote().equals(desiredValue)
        ).collect(Collectors.toList());
    }


    protected Summary getSummaryCases(String type) {
        List<String> statuses = Stream.of(ElectionStatus.FINAL.getValue(), ElectionStatus.OPEN.getValue()).
                map(String::toLowerCase).
                collect(Collectors.toList());
        List<Election> openElections = electionDAO.findElectionsWithFinalVoteByTypeAndStatus(type, statuses);
        Integer totalPendingCases = openElections == null ? 0 : openElections.size();
        Integer totalPositiveCases = electionDAO.findTotalElectionsByTypeStatusAndVote(type, ElectionStatus.CLOSED.getValue(), true);
        Integer totalNegativeCases = electionDAO.findTotalElectionsByTypeStatusAndVote(type, ElectionStatus.CLOSED.getValue(), false);
        return createSummary(totalPendingCases, totalPositiveCases, totalNegativeCases);
    }

    protected Summary getAccessSummaryCases(String type) {
        List<String> statuses = Stream.of(ElectionStatus.FINAL.getValue(), ElectionStatus.OPEN.getValue()).
                map(String::toLowerCase).
                collect(Collectors.toList());
        List<Election> openElections = electionDAO.findElectionsWithFinalVoteByTypeAndStatus(type, statuses);
        Integer totalPendingCases = openElections == null ? 0 : openElections.size();
        Integer totalPositiveCases = voteDAO.findTotalFinalVoteByElectionTypeAndVote(type, true);
        Integer totalNegativeCases = voteDAO.findTotalFinalVoteByElectionTypeAndVote(type, false);
        return createSummary(totalPendingCases, totalPositiveCases, totalNegativeCases);
    }

    private Summary createSummary(Integer totalPendingCases,
                                  Integer totalPositiveCases, Integer totalNegativeCases) {
        Summary summary = new Summary();
        summary.setPendingCases(totalPendingCases);
        summary.setReviewedNegativeCases(totalNegativeCases);
        summary.setReviewedPositiveCases(totalPositiveCases);
        return summary;
    }

    public File describeConsentSummaryDetail() {
        File file = null;
        try {
            file = File.createTempFile("summary", ".txt");
            try (FileWriter summaryWriter = new FileWriter(file)) {
                List<String> statuses = Stream.of(ElectionStatus.CLOSED.getValue(), ElectionStatus.CANCELED.getValue()).
                        map(String::toLowerCase).
                        collect(Collectors.toList());
                List<Election> reviewedElections = electionDAO.findElectionsWithFinalVoteByTypeAndStatus(ElectionType.TRANSLATE_DUL.getValue(), statuses);
                if (!CollectionUtils.isEmpty(reviewedElections)) {
                    List<String> consentIds = reviewedElections.stream().map(Election::getReferenceId).collect(Collectors.toList());
                    List<Integer> electionIds = reviewedElections.stream().map(Election::getElectionId).collect(Collectors.toList());
                    Integer maxNumberOfDACMembers = voteDAO.findMaxNumberOfDACMembers(electionIds);
                    setSummaryHeader(summaryWriter, maxNumberOfDACMembers);
                    Collection<Consent> consents = consentDAO.findConsentsFromConsentsIDs(consentIds);
                    List<Vote> votes = voteDAO.findVotesByElectionIds(electionIds);
                    Collection<Integer> dacUserIds = votes.stream().map(Vote::getDacUserId).collect(Collectors.toSet());
                    Collection<User> users = userDAO.findUsers(dacUserIds);
                    for (Election election : reviewedElections) {
                        Consent electionConsent = consents.stream().filter(c -> c.getConsentId().equals(election.getReferenceId())).collect(singletonCollector());
                        List<Vote> electionVotes = votes.stream().filter(ev -> ev.getElectionId().equals(election.getElectionId())).collect(Collectors.toList());
                        List<Integer> electionVotesUserIds = electionVotes.stream().map(Vote::getDacUserId).collect(Collectors.toList());
                        Collection<User> electionUsers = users.stream().filter(du -> electionVotesUserIds.contains(du.getDacUserId())).collect(Collectors.toSet());
                        List<Vote> electionDACVotes = electionVotes.stream().filter(ev -> ev.getType().equals("DAC")).collect(Collectors.toList());
                        Vote chairPersonVote =  electionVotes.stream().filter(ev -> ev.getType().equals(CHAIRPERSON)).collect(singletonCollector());
                        User chairPerson =  users.stream().filter(du -> du.getDacUserId().equals(chairPersonVote.getDacUserId())).collect(singletonCollector());
                        summaryWriter.write(delimiterCheck(electionConsent.getName()) + SEPARATOR);
                        summaryWriter.write(election.getVersion() + SEPARATOR);
                        summaryWriter.write(election.getStatus() + SEPARATOR);
                        summaryWriter.write(booleanToString(election.getArchived()) + SEPARATOR);
                        summaryWriter.write(delimiterCheck(electionConsent.getTranslatedUseRestriction())+ SEPARATOR);
                        summaryWriter.write(formatLongToDate(electionConsent.getCreateDate().getTime()) + SEPARATOR);
                        summaryWriter.write( chairPerson.getDisplayName() + SEPARATOR);
                        summaryWriter.write( booleanToString(chairPersonVote.getVote()) + SEPARATOR);
                        summaryWriter.write( nullToString(chairPersonVote.getRationale()) + SEPARATOR);
                        if (CollectionUtils.isNotEmpty(electionDACVotes)) {
                            for (Vote vote : electionDACVotes) {
                                List<User> user = electionUsers.stream().filter(du -> du.getDacUserId().equals(vote.getDacUserId())).collect(Collectors.toList());
                                summaryWriter.write( user.get(0).getDisplayName() + SEPARATOR);
                                summaryWriter.write( booleanToString(vote.getVote()) + SEPARATOR);
                                summaryWriter.write( nullToString(vote.getRationale())+ SEPARATOR);
                            }
                            for (int i = 0; i < (maxNumberOfDACMembers - electionVotes.size()); i++) {
                                summaryWriter.write(
                                        SEPARATOR);
                            }
                        }
                        summaryWriter.write(END_OF_LINE);
                    }
                }else file = null;
                summaryWriter.flush();
            }
            return file;
        } catch (Exception e) {
            logger.error("There is an error trying to create statistics file, error: "+ e.getMessage());
        }
        return file;
    }

    public File describeDataAccessRequestSummaryDetail() {
        File file = null;
        try {
            file = File.createTempFile("DAR_summary", ".txt");
            try (FileWriter summaryWriter = new FileWriter(file)) {
                List<Election> reviewedElections = electionDAO.findElectionsWithFinalVoteByTypeAndStatus(ElectionType.DATA_ACCESS.getValue(), ElectionStatus.CLOSED.getValue());
                List<Election> reviewedRPElections = electionDAO.findElectionsWithFinalVoteByTypeAndStatus(ElectionType.RP.getValue(), ElectionStatus.CLOSED.getValue());
                if (!CollectionUtils.isEmpty(reviewedElections)) {
                    List<String> referenceIds = reviewedElections.stream().map(Election::getReferenceId).collect(Collectors.toList());
                    List<DataAccessRequest> dataAccessRequests = dataAccessRequestService.getDataAccessRequestsByReferenceIds(referenceIds);
                    List<Integer> datasetIds = dataAccessRequests.stream().
                            map(dar -> Objects.nonNull(dar) && Objects.nonNull(dar.getData()) ? dar.getData().getDatasetIds() : new ArrayList<Integer>()).
                            flatMap(List::stream).
                            collect(Collectors.toList());
                    List<Association> associations = datasetDAO.getAssociationsForDataSetIdList(new ArrayList<>(datasetIds));
                    List<String> associatedConsentIds =   associations.stream().map(Association::getConsentId).collect(Collectors.toList());
                    List<Election> reviewedConsentElections = electionDAO.findLastElectionsWithFinalVoteByReferenceIdsTypeAndStatus(associatedConsentIds, ElectionStatus.CLOSED.getValue());
                    List<Integer> darElectionIds = reviewedElections.stream().map(Election::getElectionId).collect(Collectors.toList());
                    List<Integer> consentElectionIds = reviewedConsentElections.stream().map(Election::getElectionId).collect(Collectors.toList());
                    List<AccessRP> accessRPList = electionDAO.findAccessRPbyElectionAccessId(darElectionIds);
                    List<Vote> votes = voteDAO.findVotesByElectionIds(darElectionIds);
                    List<Integer> rpElectionIds = reviewedRPElections.stream().map(Election::getElectionId).collect(Collectors.toList());
                    List<Vote> rpVotes;
                    if (CollectionUtils.isNotEmpty(rpElectionIds)) {
                        rpVotes = voteDAO.findVotesByElectionIds(rpElectionIds);
                    } else rpVotes = null;
                    List<Vote> consentVotes = voteDAO.findVotesByElectionIds(consentElectionIds);
                    List<Match> matchList = matchDAO.findMatchesForPurposeIds(referenceIds);
                    Collection<Integer> dacUserIds = votes.stream().map(Vote::getDacUserId).collect(Collectors.toSet());
                    Collection<User> users = userDAO.findUsers(dacUserIds);
                    Integer maxNumberOfDACMembers = voteDAO.findMaxNumberOfDACMembers(darElectionIds);
                    setSummaryHeaderDataAccessRequest(summaryWriter, maxNumberOfDACMembers);
                    for (Election election : reviewedElections) {

                        List<Vote> electionVotes = votes.stream().filter(ev -> ev.getElectionId().equals(election.getElectionId())).collect(Collectors.toList());
                        List<Integer> electionVotesUserIds = electionVotes.stream().filter(v -> v.getType().equalsIgnoreCase(VoteType.DAC.getValue())).map(Vote::getDacUserId).collect(Collectors.toList());
                        Collection<User> electionUsers = users.stream().filter(du -> electionVotesUserIds.contains(du.getDacUserId())).collect(Collectors.toSet());

                        Vote finalVote =  electionVotes.stream().filter(v -> v.getType().equalsIgnoreCase(VoteType.FINAL.getValue())).collect(singletonCollector());
                        Vote chairPersonVote =  electionVotes.stream().filter(v -> v.getType().equalsIgnoreCase(VoteType.CHAIRPERSON.getValue())).collect(singletonCollector());
                        Vote  chairPersonRPVote = null;
                        Vote agreementVote = null;
                        if (CollectionUtils.isNotEmpty(reviewedRPElections) && CollectionUtils.isNotEmpty(accessRPList)) {
                            agreementVote =  electionVotes.stream().filter(v -> v.getType().equalsIgnoreCase(VoteType.AGREEMENT.getValue())).collect(singletonCollector());
                            AccessRP accessRP =  accessRPList.stream().filter(arp -> arp.getElectionAccessId().equals(election.getElectionId())).collect(singletonCollector());
                            if (Objects.nonNull(accessRP) && Objects.nonNull(rpVotes)) {
                                chairPersonRPVote = rpVotes.stream()
                                    .filter(v -> v.getElectionId().equals(accessRP.getElectionRPId()))
                                    .filter(v -> v.getType().equalsIgnoreCase(VoteType.CHAIRPERSON.getValue()))
                                    .collect(singletonCollector());
                            }
                        }
                        User chairPerson =  users.stream().filter(du -> du.getDacUserId().equals(finalVote.getDacUserId())).collect(singletonCollector());
                        Match match;
                        try {
                            match = matchList.stream().filter(m -> m.getPurpose().equals(election.getReferenceId())).collect(singletonCollector());
                        } catch (IllegalStateException e){
                            match = null;
                        }
                        DataAccessRequest dar = findAssociatedDAR(dataAccessRequests, election.getReferenceId());
                        if (Objects.nonNull(dar) && Objects.nonNull(dar.getData())) {
                            List<Integer> datasetId = dar.getData().getDatasetIds();
                            if (CollectionUtils.isNotEmpty(datasetId)) {
                                Association association = associations.stream().filter((as) -> as.getDataSetId().equals(datasetId.get(0))).collect(singletonCollector());
                                Election consentElection = reviewedConsentElections.stream().filter(re -> re.getReferenceId().equals(association.getConsentId())).collect(singletonCollector());
                                List<Vote> electionConsentVotes = consentVotes.stream().filter(cv -> cv.getElectionId().equals(consentElection.getElectionId())).collect(Collectors.toList());
                                Vote chairPersonConsentVote =  electionConsentVotes.stream().filter(v -> v.getType().equalsIgnoreCase(VoteType.CHAIRPERSON.getValue())).collect(singletonCollector());
                                summaryWriter.write(dar.getData().getDarCode() + SEPARATOR);
                                summaryWriter.write(formatLongToDate(election.getCreateDate().getTime()) + SEPARATOR);
                                summaryWriter.write(chairPerson.getDisplayName() + SEPARATOR);
                                summaryWriter.write( booleanToString(finalVote.getVote()) + SEPARATOR);
                                summaryWriter.write( nullToString(finalVote.getRationale()) + SEPARATOR);
                                if (match != null) {
                                    summaryWriter.write(booleanToString(match.getMatch()) + SEPARATOR);
                                } else {
                                    summaryWriter.write(MANUAL_REVIEW + SEPARATOR);
                                }
                                if (agreementVote != null) {
                                    summaryWriter.write(booleanToString(agreementVote.getVote()) + SEPARATOR);
                                    summaryWriter.write(nullToString(agreementVote.getRationale()) + SEPARATOR);
                                } else {
                                    summaryWriter.write("-" + SEPARATOR);
                                    summaryWriter.write("-" + SEPARATOR);
                                }
                                User u = userDAO.findUserById(dar.getUserId());
                                summaryWriter.write( u.getDisplayName()  + SEPARATOR);
                                summaryWriter.write( dar.getData().getProjectTitle() + SEPARATOR);
                                List<String> dataSetUUIds = new ArrayList<>();
                                for (Integer id : datasetId) {
                                    dataSetUUIds.add(DataSet.parseAliasToIdentifier(id));
                                }
                                summaryWriter.write( StringUtils.join(dataSetUUIds, ",")  + SEPARATOR);
                                summaryWriter.write( formatLongToDate(dar.getSortDate().getTime())  + SEPARATOR);
                                for (User user : electionUsers){
                                    summaryWriter.write( user.getDisplayName() + SEPARATOR);
                                }
                                for (int i = 0; i < (maxNumberOfDACMembers - electionUsers.size()); i++) {
                                    summaryWriter.write(
                                            SEPARATOR);
                                }
                                summaryWriter.write( booleanToString(Objects.isNull(dar.getData().getRestriction()))+ SEPARATOR);

                                if (Objects.nonNull(chairPersonVote)) {
                                    summaryWriter.write(booleanToString(chairPersonVote.getVote()) + SEPARATOR);
                                    summaryWriter.write(nullToString(chairPersonVote.getRationale()) + SEPARATOR);
                                } else {
                                    summaryWriter.write(nullToString(null) + SEPARATOR);
                                    summaryWriter.write(nullToString(null) + SEPARATOR);
                                }

                                if (Objects.nonNull(chairPersonRPVote)) {
                                    summaryWriter.write(booleanToString(chairPersonRPVote.getVote()) + SEPARATOR);
                                    summaryWriter.write(nullToString(chairPersonRPVote.getRationale()) + SEPARATOR);
                                } else {
                                    summaryWriter.write(nullToString(null) + SEPARATOR);
                                    summaryWriter.write(nullToString(null) + SEPARATOR);
                                }

                                if (Objects.nonNull(chairPersonConsentVote)) {
                                    summaryWriter.write(booleanToString(chairPersonConsentVote.getVote()) + SEPARATOR);
                                    summaryWriter.write(nullToString(chairPersonConsentVote.getRationale()) + SEPARATOR);
                                } else {
                                    summaryWriter.write(nullToString(null) + SEPARATOR);
                                    summaryWriter.write(nullToString(null) + SEPARATOR);
                                }
                            }
                        }
                        summaryWriter.write(END_OF_LINE);
                    }
                }else file = null;
                summaryWriter.flush();
            }
            return file;
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return file;
    }

    public File describeDataSetElectionsVotesForDar(String referenceId) {
        File file = null;
        try {
            file = File.createTempFile("dar" + referenceId + "DatasetElectionsDetail", ".txt");
            try (FileWriter summaryWriter = new FileWriter(file)) {
                List<Election> elections = electionDAO.findLastElectionsByReferenceIdAndType(referenceId, ElectionType.DATA_SET.getValue());
                Map<Integer, List<Vote>> electionsData = new HashMap<>();
                int maxNumberOfVotes = 0;
                for(Election e: elections){
                    List<Vote> votes = voteDAO.findVoteByTypeAndElectionId(e.getElectionId(), VoteType.DATA_OWNER.getValue());
                    electionsData.put(e.getElectionId(), votes);
                    if(votes.size() > maxNumberOfVotes){
                        maxNumberOfVotes = votes.size();
                    }
                }
                setDatasetElectionsHeader(summaryWriter, maxNumberOfVotes);

                String dar_code = dataAccessRequestService.findByReferenceId(referenceId).getData().getDarCode();
                String dar_election_result;
                try{
                    dar_election_result = (electionDAO.findLastElectionByReferenceIdAndType(referenceId, ElectionType.DATA_ACCESS.getValue())).getFinalAccessVote() ? "Approved" : "Denied";
                } catch (NullPointerException e){
                    dar_election_result = "Pending";
                }
                for (Election election : elections) {
                    summaryWriter.write( dar_code + SEPARATOR);
                    summaryWriter.write( dar_election_result + SEPARATOR);
                    DataSet dataset = datasetDAO.findDataSetById(electionDAO.getDatasetIdByElectionId(election.getElectionId()));
                    summaryWriter.write( dataset.getObjectId() + SEPARATOR);
                    summaryWriter.write( dataset.getName() + SEPARATOR);
                    summaryWriter.write(electionResult(election.getFinalAccessVote()) + SEPARATOR);
                    List<Vote> votes = electionsData.get(election.getElectionId());
                    for(Vote datasetVote : votes){
                        User user = userDAO.findUserById(datasetVote.getDacUserId());
                        summaryWriter.write(user.getDisplayName() + SEPARATOR);
                        summaryWriter.write(user.getEmail() + SEPARATOR);
                        summaryWriter.write(datasetVoteResult(datasetVote) + SEPARATOR);
                        summaryWriter.write(datasetVote.getRationale() == null ? "None" : datasetVote.getRationale());
                        summaryWriter.write(SEPARATOR);
                    }
                   summaryWriter.write(END_OF_LINE);
                }
            }
            return file;
        } catch (Exception e) {
            logger.error("There is an error trying to create resume of dataset votes file, error: "+ e.getMessage());
        }
        return file;
    }

    private String electionResult(Boolean result){
        try{
            if(result){
                return "Approved";
            } else {
                return "Denied";
            }
        } catch( NullPointerException e) {
            return "Pending";
        }
    }

    private String datasetVoteResult(Vote vote){
        try{
            if(vote.getVote()){
                return "Approved";
            } else {
                return "Denied";
            }
        } catch( NullPointerException e) {
            if(vote.getHasConcerns()){
                return "Denied";
            }
            return "Pending";
        }
    }

    private void setSummaryHeader(FileWriter summaryWriter , Integer maxNumberOfDACMembers) throws IOException {
        summaryWriter.write(
                HeaderSummary.CONSENT.getValue() + SEPARATOR +
                        HeaderSummary.VERSION.getValue() + SEPARATOR +
                        HeaderSummary.STATUS.getValue() + SEPARATOR +
                        HeaderSummary.ARCHIVED.getValue() + SEPARATOR +
                        HeaderSummary.STRUCT_LIMITATIONS.getValue() + SEPARATOR +
                        HeaderSummary.DATE.getValue() + SEPARATOR +
                        HeaderSummary.CHAIRPERSON.getValue() + SEPARATOR +
                        HeaderSummary.FINAL_DECISION.getValue() + SEPARATOR +
                        HeaderSummary.FINAL_DECISION_RATIONALE.getValue() + SEPARATOR);
        for (int i = 1; i < maxNumberOfDACMembers; i++) {
            summaryWriter.write(
                    HeaderSummary.USER.getValue() + SEPARATOR +
                    HeaderSummary.VOTE.getValue() + SEPARATOR +
                    HeaderSummary.RATIONALE.getValue() + SEPARATOR);
        }
        summaryWriter.write(
                HeaderSummary.USER.getValue() + SEPARATOR +
                HeaderSummary.VOTE.getValue() + SEPARATOR +
                HeaderSummary.RATIONALE.getValue()+ END_OF_LINE);
    }

    private void setDatasetElectionsHeader(FileWriter summaryWriter , Integer maxNumberOfVotes) throws IOException {
        summaryWriter.write(
                HeaderSummary.DATA_REQUEST_ID.getValue() + SEPARATOR +
                        HeaderSummary.FINAL_DECISION_DAR.getValue() + SEPARATOR +
                        HeaderSummary.DATASET_ID.getValue() + SEPARATOR +
                        HeaderSummary.DATASET_NAME.getValue() + SEPARATOR +
                        HeaderSummary.DATASET_FINAL_STATUS.getValue() + SEPARATOR);
        for (int i = 0; i < maxNumberOfVotes; i++) {
            summaryWriter.write(
                    HeaderSummary.DATA_OWNER_NAME.getValue() + SEPARATOR +
                            HeaderSummary.DATA_OWNER_EMAIL.getValue() + SEPARATOR +
                            HeaderSummary.DATA_OWNER_VOTE.getValue() + SEPARATOR +
                            HeaderSummary.DATA_OWNER_COMMENT.getValue() + SEPARATOR);
        }
        summaryWriter.write(END_OF_LINE);
    }

    private void setSummaryHeaderDataAccessRequest(FileWriter summaryWriter , Integer maxNumberOfDACMembers) throws IOException {
        summaryWriter.write(
                HeaderSummary.DATA_REQUEST_ID.getValue() + SEPARATOR +
                        HeaderSummary.DATE.getValue() + SEPARATOR +
                        HeaderSummary.CHAIRPERSON.getValue() + SEPARATOR +
                        HeaderSummary.FINAL_DECISION.getValue() + SEPARATOR +
                        HeaderSummary.FINAL_DECISION_RATIONALE.getValue() + SEPARATOR +
                        HeaderSummary.VAULT_DECISION.getValue() + SEPARATOR +
                        HeaderSummary.VAULT_VS_DAC_AGREEMENT.getValue() + SEPARATOR +
                        HeaderSummary.CHAIRPERSON_FEEDBACK.getValue() + SEPARATOR +
                        HeaderSummary.RESEARCHER.getValue() + SEPARATOR +
                        HeaderSummary.PROJECT_TITLE.getValue() + SEPARATOR +
                        HeaderSummary.DATASET_ID.getValue() + SEPARATOR +
                        HeaderSummary.DATA_ACCESS_SUBM_DATE.getValue() + SEPARATOR);
        for (int i = 1; i <= maxNumberOfDACMembers; i++) {
            summaryWriter.write(
                    HeaderSummary.DAC_MEMBERS.getValue() + SEPARATOR);
        }
        summaryWriter.write(
                HeaderSummary.REQUIRE_MANUAL_REVIEW.getValue() + SEPARATOR +
                        HeaderSummary.FINAL_DECISION_DAR.getValue() + SEPARATOR +
                        HeaderSummary.FINAL_RATIONALE_DAR.getValue() + SEPARATOR +
                        HeaderSummary.FINAL_DECISION_RP.getValue() + SEPARATOR +
                        HeaderSummary.FINAL_RATIONALE_RP.getValue() + SEPARATOR +
                        HeaderSummary.FINAL_DECISION_DUL.getValue() + SEPARATOR +
                        HeaderSummary.FINAL_RATIONALE_DUL.getValue() + END_OF_LINE);
    }

    public static <T> Collector<T, ?, T> singletonCollector() {
        return Collectors.collectingAndThen(
                Collectors.toList(),
                list -> {
                    if(CollectionUtils.isEmpty(list)){
                        return null;
                    }
                    if (list.size() != 1) {
                        throw new IllegalStateException();
                    }
                    return list.get(0);
                }
        );
    }

    private DataAccessRequest findAssociatedDAR(List<DataAccessRequest> dataAccessRequests, String referenceId) {
        Optional<DataAccessRequest> darOption = dataAccessRequests.stream().
                filter(d -> Objects.nonNull(d) ? d.getReferenceId().equalsIgnoreCase(referenceId) : false).
                findFirst();
        return darOption.orElse(null);
    }

    private String booleanToString(Boolean b) {
        if(b != null) {
            return b ? "YES" : "NO";
        }
        return "-";
    }

    private String nullToString(String b) {
        return b != null && !b.isEmpty()  ? b : "-";
    }


    public String formatLongToDate(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        int day = cal.get(Calendar.DAY_OF_MONTH);
        int month = cal.get(Calendar.MONTH) + 1;
        int year = cal.get(Calendar.YEAR);
        return String.format("%d/%d/%d", month, day, year);
    }

    public String delimiterCheck(String delimitatedString){
        if (StringUtils.isNotEmpty(delimitatedString)) {
            return TEXT_DELIMITER +
                    delimitatedString.replaceAll(TEXT_DELIMITER, "'") + TEXT_DELIMITER;
        } else {
            return "";
        }

    }

}
