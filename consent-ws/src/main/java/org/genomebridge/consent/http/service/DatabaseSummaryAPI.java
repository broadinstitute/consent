package org.genomebridge.consent.http.service;

import org.genomebridge.consent.http.db.DACUserDAO;
import org.genomebridge.consent.http.db.ElectionDAO;
import org.genomebridge.consent.http.db.VoteDAO;
import org.genomebridge.consent.http.enumeration.ElectionStatus;
import org.genomebridge.consent.http.enumeration.ElectionType;
import org.genomebridge.consent.http.enumeration.HeaderSummary;
import org.genomebridge.consent.http.models.DACUser;
import org.genomebridge.consent.http.models.Election;
import org.genomebridge.consent.http.models.Summary;
import org.genomebridge.consent.http.models.Vote;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implementation class for VoteAPI on top of ElectionDAO database support.
 */
public class DatabaseSummaryAPI extends AbstractSummaryAPI {

    private VoteDAO voteDAO;
    private ElectionDAO electionDAO;
    private DACUserDAO dacUserDAO;
    private static final String SEPARATOR = "\t";
    private final String END_OF_LINE = System.lineSeparator();

    /**
     * Initialize the singleton API instance using the provided DAO. This method
     * should only be called once during application initialization (from the
     * run() method). If called a second time it will throw an
     * IllegalStateException. Note that this method is not synchronized, as it
     * is not intended to be called more than once.
     *
     * @param dao The Data Access Object instance that the API should use to
     *            read/write data.
     */
    public static void initInstance(VoteDAO dao, ElectionDAO electionDAO, DACUserDAO userDAO) {
        SummaryAPIHolder.setInstance(new DatabaseSummaryAPI(dao, electionDAO, userDAO));

    }

    /**
     * The constructor is private to force use of the factory methods and
     * enforce the singleton pattern.
     *
     * @param dao The Data Access Object used to read/write data.
     */
    private DatabaseSummaryAPI(VoteDAO dao, ElectionDAO electionDAO, DACUserDAO dacUserDAO) {
        this.voteDAO = dao;
        this.electionDAO = electionDAO;
        this.dacUserDAO = dacUserDAO;
    }

    @Override
    public Summary describeConsentSummaryCases() {
        String type = electionDAO.findElectionTypeByType(ElectionType.TRANSLATE_DUL.getValue());
        return getSummaryCases(type);
    }

    @Override
    public Summary describeDataRequestSummaryCases(String electionType) {
        String type = electionDAO.findElectionTypeByType(electionType);
        Summary summary;
        if(electionType.equals(ElectionType.DATA_ACCESS.getValue())){
            summary = getAccessSummaryCases(type);
        }else{
            summary = getSummaryCases(type);
        }
        return summary;
    }

    private Summary getSummaryCases(String type) {
        List<String> status = Arrays.asList(ElectionStatus.FINAL.getValue(), ElectionStatus.OPEN.getValue());
        List<Election> openElections = electionDAO.findElectionsByTypeAndStatus(type, status);
        Integer totalPendingCases = openElections == null ? 0 : openElections.size();
        Integer totalPositiveCases = electionDAO.findTotalElectionsByTypeStatusAndVote(type, ElectionStatus.CLOSED.getValue(), true);
        Integer totalNegativeCases = electionDAO.findTotalElectionsByTypeStatusAndVote(type, ElectionStatus.CLOSED.getValue(), false);
        return createSummary(totalPendingCases, totalPositiveCases, totalNegativeCases);
    }

    private Summary getAccessSummaryCases(String type) {
        List<String> status = Arrays.asList(ElectionStatus.FINAL.getValue(), ElectionStatus.OPEN.getValue());
        List<Election> openElections = electionDAO.findElectionsByTypeAndStatus(type, status);
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

    @Override
    public File describeConsentSummaryDetail() {
        File file = null;
        try {
            file = File.createTempFile("summary", ".txt");
            try (FileWriter summaryWriter = new FileWriter(file)) {
                List<Election> reviewedElections = electionDAO.findElectionsByTypeAndStatus("2", ElectionStatus.CLOSED.getValue());
                setSummaryHeader(summaryWriter);
                if (reviewedElections != null && reviewedElections.size() > 0) {
                    for (Election election : reviewedElections) {
                        List<Vote> votes = voteDAO.findDACVotesByElectionId(election.getElectionId());
                        if (votes != null && votes.size() > 0) {
                            for (Vote vote : votes) {
                                DACUser user = dacUserDAO.findDACUserById(vote.getDacUserId());
                                summaryWriter.write(election.getReferenceId() + SEPARATOR);
                                summaryWriter.write(user.getDisplayName() + SEPARATOR);
                                summaryWriter.write(vote.getVote() + SEPARATOR);
                                summaryWriter.write(vote.getRationale() + SEPARATOR);
                                summaryWriter.write(election.getFinalVote() + SEPARATOR);
                                summaryWriter.write(election.getFinalRationale() + SEPARATOR);
                                //sdul is pending
                                summaryWriter.write("sDUL" + SEPARATOR);
                                summaryWriter.write(END_OF_LINE);
                            }
                        }
                        
                    }
                }
                summaryWriter.flush();
            }
            return file;
        } catch (Exception ignored) {

        }
        return file;
    }


    private void setSummaryHeader(FileWriter summaryWriter) throws IOException {
        summaryWriter.write(
                HeaderSummary.CASEID.getValue() + SEPARATOR +
                        HeaderSummary.USER.getValue() + SEPARATOR +
                        HeaderSummary.VOTE.getValue() + SEPARATOR +
                        HeaderSummary.RATIONALE.getValue() + SEPARATOR +
                        HeaderSummary.FINAL_VOTE.getValue() + SEPARATOR +
                        HeaderSummary.FINAL_RATIONALE.getValue() + SEPARATOR +
                        HeaderSummary.SDUL.getValue() + END_OF_LINE);

    }


}
