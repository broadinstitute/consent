package org.broadinstitute.consent.http.service;

import com.google.inject.Inject;
import org.broadinstitute.consent.http.db.DACUserDAO;
import org.broadinstitute.consent.http.db.DataSetAssociationDAO;
import org.broadinstitute.consent.http.db.DataSetDAO;
import org.broadinstitute.consent.http.db.ElectionDAO;
import org.broadinstitute.consent.http.db.VoteDAO;
import org.broadinstitute.consent.http.enumeration.ElectionType;
import org.broadinstitute.consent.http.enumeration.UserRoles;
import org.broadinstitute.consent.http.enumeration.VoteType;
import org.broadinstitute.consent.http.models.DACUser;
import org.broadinstitute.consent.http.models.Dac;
import org.broadinstitute.consent.http.models.Election;
import org.broadinstitute.consent.http.models.Vote;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class VoteService {

    private DACUserDAO dacUserDAO;
    private DataSetAssociationDAO dataSetAssociationDAO;
    private DataSetDAO datasetDAO;
    private ElectionDAO electionDAO;
    private VoteDAO voteDAO;

    @Inject
    public VoteService(DACUserDAO dacUserDAO, DataSetAssociationDAO dataSetAssociationDAO,
                       DataSetDAO datasetDAO, ElectionDAO electionDAO, VoteDAO voteDAO) {
        this.dacUserDAO = dacUserDAO;
        this.dataSetAssociationDAO = dataSetAssociationDAO;
        this.datasetDAO = datasetDAO;
        this.electionDAO = electionDAO;
        this.voteDAO = voteDAO;
    }

    /**
     * Find all votes for a reference id. This can find votes for multiple elections as there
     * are usually multiple forms of election per thing being voted upon.
     *
     * @param referenceId The reference id for the election.
     * @return Collection of votes for the given reference id
     */
    public Collection<Vote> findVotesByReferenceId(String referenceId) {
        return voteDAO.findVotesByReferenceId(referenceId);
    }

    /**
     * Update votes such that they have the provided value and rationale.
     *
     * @param voteList  Collection of votes to advance
     * @param voteValue The new vote value
     * @param rationale The new rationale
     */
    public void advanceVotes(Collection<Vote> voteList, boolean voteValue, String rationale) {
        Date now = new Date();
        voteList.forEach(v -> {
            v.setUpdateDate(now);
            v.setCreateDate(now);
            v.setVote(voteValue);
            v.setRationale(rationale);
            updateVote(v);
        });
    }

    /**
     * @param vote Vote to update
     * @return The updated Vote
     */
    public Vote updateVote(Vote vote) {
        validateVote(vote);
        Date now = new Date();
        voteDAO.updateVote(
                vote.getVote(),
                vote.getRationale(),
                (vote.getUpdateDate() == null) ? now : vote.getUpdateDate(),
                vote.getVoteId(),
                vote.getIsReminderSent(),
                vote.getElectionId(),
                (vote.getCreateDate() == null) ? now : vote.getCreateDate(),
                vote.getHasConcerns()
        );
        return voteDAO.findVoteById(vote.getVoteId());
    }

    /**
     * Create votes for an election
     * <p>
     * TODO: Refactor duplicated code when DatabaseElectionAPI is fully replaced by ElectionService
     *
     * @param election       The Election
     * @param electionType   The Election type
     * @param isManualReview Is this a manual review election
     * @return List of votes
     */
    @SuppressWarnings("DuplicatedCode")
    public List<Vote> createVotes(Election election, ElectionType electionType, Boolean isManualReview) {
        Set<DACUser> dacUsers;
        if (election.getDataSetId() != null) {
            Dac electionDac = datasetDAO.findDacForDataset(election.getDataSetId());
            if (electionDac != null) {
                dacUsers = dacUserDAO.findDACUsersEnabledToVoteByDAC(electionDac.getDacId());
            } else {
                // This case represents: Election has a dataset, but there is no DAC for the dataset
                dacUsers = dacUserDAO.findNonDACUsersEnabledToVote();
            }
        } else {
            Dac consentDac = electionDAO.findDacForConsentElection(election.getElectionId());
            if (consentDac != null) {
                dacUsers = dacUserDAO.findDACUsersEnabledToVoteByDAC(consentDac.getDacId());
            } else {
                // This case represents: Election does not have a dataset, and therefore, no DAC for it
                dacUsers = dacUserDAO.findNonDACUsersEnabledToVote();
            }
        }
        List<Vote> votes = new ArrayList<>();
        if (dacUsers != null) {
            for (DACUser user : dacUsers) {
                Integer dacVoteId = voteDAO.insertVote(user.getDacUserId(), election.getElectionId(), VoteType.DAC.getValue());
                votes.add(voteDAO.findVoteById(dacVoteId));
                if (isChairPerson(user)) {
                    Integer chairVoteId = voteDAO.insertVote(user.getDacUserId(), election.getElectionId(), VoteType.CHAIRPERSON.getValue());
                    votes.add(voteDAO.findVoteById(chairVoteId));
                    // Requires Chairperson role to create a final and agreement vote in the Data Access case
                    if (electionType.equals(ElectionType.DATA_ACCESS)) {
                        Integer finalVoteId = voteDAO.insertVote(user.getDacUserId(), election.getElectionId(), VoteType.FINAL.getValue());
                        votes.add(voteDAO.findVoteById(finalVoteId));
                        if (!isManualReview) {
                            Integer agreementVoteId = voteDAO.insertVote(user.getDacUserId(), election.getElectionId(), VoteType.AGREEMENT.getValue());
                            votes.add(voteDAO.findVoteById(agreementVoteId));
                        }
                    }
                }
            }
        }
        return votes;
    }

    /**
     * Create Votes for a data owner election
     *
     * @param election Election
     * @return Votes for the election
     */
    @SuppressWarnings("UnusedReturnValue")
    public List<Vote> createDataOwnersReviewVotes(Election election) {
        List<Integer> dataOwners = dataSetAssociationDAO.getDataOwnersOfDataSet(election.getDataSetId());
        voteDAO.insertVotes(dataOwners, election.getElectionId(), VoteType.DATA_OWNER.getValue());
        return voteDAO.findVotesByElectionIdAndType(election.getElectionId(), VoteType.DATA_OWNER.getValue());
    }

    private boolean isChairPerson(DACUser user) {
        return user.getRoles().
                stream().
                anyMatch(userRole -> userRole.getRoleId().equals(UserRoles.CHAIRPERSON.getRoleId()));
    }

    /**
     * Convenience method to ensure Vote non-nullable values are populated
     *
     * @param vote The Vote to validate
     */
    private void validateVote(Vote vote) {
        if (vote == null ||
                vote.getVoteId() == null ||
                vote.getDacUserId() == null ||
                vote.getElectionId() == null) {
            throw new IllegalArgumentException("Invalid vote: " + vote);
        }
        if (voteDAO.findVoteById(vote.getVoteId()) == null) {
            throw new IllegalArgumentException("No vote exists with the id of " + vote.getVoteId());
        }
    }

}
