package org.broadinstitute.consent.http.service;

import com.google.common.io.Resources;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.broadinstitute.consent.http.AbstractTest;
import org.broadinstitute.consent.http.ConsentApplication;
import org.broadinstitute.consent.http.configurations.ConsentConfiguration;
import org.broadinstitute.consent.http.db.ConsentDAO;
import org.broadinstitute.consent.http.db.DACUserDAO;
import org.broadinstitute.consent.http.db.DataSetDAO;
import org.broadinstitute.consent.http.db.ElectionDAO;
import org.broadinstitute.consent.http.db.MailMessageDAO;
import org.broadinstitute.consent.http.db.VoteDAO;
import org.broadinstitute.consent.http.enumeration.ElectionStatus;
import org.broadinstitute.consent.http.enumeration.ElectionType;
import org.broadinstitute.consent.http.enumeration.UserRoles;
import org.broadinstitute.consent.http.models.Consent;
import org.broadinstitute.consent.http.models.DACUser;
import org.broadinstitute.consent.http.models.Election;
import org.broadinstitute.consent.http.models.UserRole;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.broadinstitute.consent.http.enumeration.UserRoles.MEMBER;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

public class ElectionAPITest extends AbstractTest {

    @Mock
    private ElectionDAO electionDAO;
    @Mock
    private ConsentDAO consentDAO;
    @Mock
    private DACUserDAO dacUserDAO;
    @Mock
    private DataAccessRequestService dataAccessRequestService;
    @Mock
    private VoteDAO voteDAO;
    @Mock
    private MailMessageDAO mailMessageDAO;
    @Mock
    private DataSetDAO dataSetDAO;

    private DatabaseElectionAPI electionAPI;

    private static final String consentId = UUID.randomUUID().toString();
    private Consent consent = new Consent();
    private DACUserDAO userDAO;

    @ClassRule
    public static final DropwizardAppRule<ConsentConfiguration> RULE = new DropwizardAppRule<>(
            ConsentApplication.class, Resources.getResource("consent-config.yml").getFile());

    @Override
    public DropwizardAppRule<ConsentConfiguration> rule() {
        return RULE;
    }

    @Before
    public void setUp() {
        userDAO = getApplicationJdbi().onDemand(DACUserDAO.class);
        MockitoAnnotations.initMocks(this);
        electionAPI = Mockito.spy(new DatabaseElectionAPI(dataAccessRequestService, electionDAO, consentDAO, dacUserDAO, voteDAO, mailMessageDAO, dataSetDAO));
        consent.setConsentId(consentId);
        consent.setTranslatedUseRestriction("Translated");
    }

    @Test
    public void testCreateConsentElectionSingleChairperson() throws Exception {
        Optional<UserRole> ur = userDAO.findUsers().stream().
                flatMap(user -> user.getRoles().stream()).
                filter(userRole -> userRole.getRoleId().equals(UserRoles.CHAIRPERSON.getRoleId())).
                findFirst();
        Assert.assertTrue(ur.isPresent());
        Set<DACUser> chairsWithRoles = userDAO.findUsersWithRoles(Collections.singletonList(ur.get().getUserId()));
        when(dacUserDAO.findNonDACUsersEnabledToVote()).thenReturn(chairsWithRoles);
        when(consentDAO.checkConsentById(consentId)).thenReturn(consentId);
        when(consentDAO.findConsentById(consentId)).thenReturn(consent);
        Election election = createConsentElection();
        when(electionDAO.findElectionWithFinalVoteById(any())).thenReturn(election);
        Election savedElection = electionAPI.createElection(election, consentId, ElectionType.TRANSLATE_DUL);
        assertNotNull(savedElection);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateConsentElectionNoDACMembers() throws Exception {
        when(dacUserDAO.findNonDACUsersEnabledToVote()).thenReturn(Collections.emptySet());
        Election election = createConsentElection();
        electionAPI.createElection(election, consentId, ElectionType.TRANSLATE_DUL);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateConsentElectionNoChair() throws Exception {
        List<Integer> memberIds = userDAO
                .findNonDACUsersEnabledToVote()
                .stream()
                .map(DACUser::getDacUserId)
                .collect(Collectors.toList());
        Set<DACUser> dacMembersWithRoles = userDAO.findUsersWithRoles(memberIds);
        Set<DACUser> membersWithRoles = dacMembersWithRoles
                .stream()
                .filter(u -> u.getRoles().contains(MEMBER))
                .collect(Collectors.toSet());
        when(dacUserDAO.findNonDACUsersEnabledToVote()).thenReturn(membersWithRoles);
        Election election = createConsentElection();
        electionAPI.createElection(election, consentId, ElectionType.TRANSLATE_DUL);
    }

    private Election createConsentElection() {
        Election election = new Election();
        election.setStatus(ElectionStatus.OPEN.getValue());
        election.setElectionType(ElectionType.TRANSLATE_DUL.getValue());
        election.setReferenceId(consentId);
        return election;
    }

}
