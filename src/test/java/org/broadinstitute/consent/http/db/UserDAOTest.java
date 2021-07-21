package org.broadinstitute.consent.http.db;

import org.apache.commons.lang3.RandomStringUtils;
import org.broadinstitute.consent.http.enumeration.RoleStatus;
import org.broadinstitute.consent.http.enumeration.UserFields;
import org.broadinstitute.consent.http.enumeration.UserRoles;
import org.broadinstitute.consent.http.models.Consent;
import org.broadinstitute.consent.http.models.Dac;
import org.broadinstitute.consent.http.models.DataSet;
import org.broadinstitute.consent.http.models.Election;
import org.broadinstitute.consent.http.models.Institution;
import org.broadinstitute.consent.http.models.User;
import org.broadinstitute.consent.http.models.UserProperty;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.broadinstitute.consent.http.enumeration.RoleStatus.getStatusByValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class UserDAOTest extends DAOTestHelper {

    @Test
    public void testFindDACUserById() {
        User user = createUserWithRole(UserRoles.ALUMNI.getRoleId());
        assertNotNull(user);
        assertFalse(user.getRoles().isEmpty());

        addUserRole(UserRoles.ADMIN.getRoleId(), user.getDacUserId());
        addUserRole(UserRoles.RESEARCHER.getRoleId(), user.getDacUserId());
        addUserRole(UserRoles.DATAOWNER.getRoleId(), user.getDacUserId());

        User user2 = userDAO.findUserById(user.getDacUserId());
        assertNotNull(user2);
        assertEquals(user.getEmail(), user2.getEmail());

        // Assert roles are fetched correctly
        assertTrue(user2.getRoles().stream()
            .anyMatch(r -> r.getRoleId().equals(UserRoles.ALUMNI.getRoleId())));
        assertTrue(user2.getRoles().stream()
            .anyMatch(r -> r.getRoleId().equals(UserRoles.ADMIN.getRoleId())));
        assertTrue(user2.getRoles().stream()
            .anyMatch(r -> r.getRoleId().equals(UserRoles.RESEARCHER.getRoleId())));
        assertTrue(user2.getRoles().stream()
            .anyMatch(r -> r.getRoleId().equals(UserRoles.DATAOWNER.getRoleId())));

        //assert institution base data is present if available
        User user3 = createUserWithInstitution();
        User queriedUser3 = userDAO.findUserById(user3.getDacUserId());
        assert(queriedUser3.getDacUserId()).equals(user3.getDacUserId());
        assertNotNull(queriedUser3.getInstitutionId());
        assert(queriedUser3.getInstitution().getId()).equals(user3.getInstitution().getId());
    }

    @Test
    public void testFindUsers_withIdCollection() {
        User user = createUser();
        Collection<User> users = userDAO.findUsers(Collections.singletonList(user.getDacUserId()));
        assertNotNull(users);
        assertFalse(users.isEmpty());
        assertEquals(1, users.size());
    }

    @Test
    public void testDescribeUsersByRole() {
        createUserWithRole(UserRoles.CHAIRPERSON.getRoleId());
        createUserWithRole(UserRoles.MEMBER.getRoleId());

        List<User> members = userDAO.describeUsersByRole(UserRoles.MEMBER.getRoleName());
        assertFalse(members.isEmpty());

        List<User> chairs = userDAO.describeUsersByRole(UserRoles.CHAIRPERSON.getRoleName());
        assertFalse(chairs.isEmpty());

        // Only case where we don't set up users by default.
        List<User> alumni = userDAO.describeUsersByRole(UserRoles.ALUMNI.getRoleName());
        assertTrue(alumni.isEmpty());

        List<User> admins = userDAO.describeUsersByRole(UserRoles.ADMIN.getRoleName());
        assertTrue(admins.isEmpty());

        List<User> researchers = userDAO.describeUsersByRole(UserRoles.RESEARCHER.getRoleName());
        assertTrue(researchers.isEmpty());

        List<User> dataOwners = userDAO.describeUsersByRole(UserRoles.DATAOWNER.getRoleName());
        assertTrue(dataOwners.isEmpty());
    }

    @Test
    public void testCheckChairpersonUser() {
        User chair = createUserWithRole(UserRoles.CHAIRPERSON.getRoleId());
        User member = createUserWithRole(UserRoles.MEMBER.getRoleId());
        assertNotNull(userDAO.checkChairpersonUser(chair.getDacUserId()));
        Assert.assertNull(userDAO.checkChairpersonUser(member.getDacUserId()));
    }

    @Test
    public void testFindDACUsersEnabledToVoteByDacEmpty() {
        Dac dac = createDac();
        Collection<User> users = userDAO.findUsersEnabledToVoteByDAC(dac.getDacId());
        assertTrue(users.isEmpty());
    }

    @Test
    public void testFindDACUsersEnabledToVoteByDacNotEmpty() {
        Dac dac = createDac();
        User chair = createUserWithRole(UserRoles.CHAIRPERSON.getRoleId());
        dacDAO.addDacMember(UserRoles.CHAIRPERSON.getRoleId(), chair.getDacUserId(), dac.getDacId());
        Collection<User> users = userDAO.findUsersEnabledToVoteByDAC(dac.getDacId());
        assertFalse(users.isEmpty());
    }

    @Test
    public void testFindNonDACUsersEnabledToVote() {
        createUserWithRole(UserRoles.CHAIRPERSON.getRoleId());
        createUserWithRole(UserRoles.MEMBER.getRoleId());
        Collection<User> users = userDAO.findNonDacUsersEnabledToVote();
        assertFalse(users.isEmpty());
    }

    @Test
    public void testFindUsersWithRoles() {
        User chair = createUserWithRole(UserRoles.ADMIN.getRoleId());
        addUserRole(UserRoles.DATAOWNER.getRoleId(), chair.getDacUserId());
        Collection<Integer> userIds = Collections.singletonList(chair.getDacUserId());
        Collection<User> users = userDAO.findUsersWithRoles(userIds);
        users.forEach(u -> assertFalse("User: " + u.getDacUserId() + " has no roles", u.getRoles().isEmpty()));
        assertEquals(1, users.size());
        User user = users.stream().findFirst().orElse(null);
        assertNotNull(user);
        assertEquals(2, user.getRoles().size());
    }

    @Test
    public void testFindUsersWithNoRoles() {
        User user = createUser();
        Collection<Integer> userIds = Collections.singletonList(user.getDacUserId());
        Collection<User> users = userDAO.findUsersWithRoles(userIds);
        users.forEach(u -> assertTrue("User: " + u.getDacUserId() + " has roles", u.getRoles().isEmpty()));
    }

    @Test
    public void testFindDACUserByEmail() {
        User user = createUser();
        addUserRole(UserRoles.ALUMNI.getRoleId(), user.getDacUserId());
        addUserRole(UserRoles.ADMIN.getRoleId(), user.getDacUserId());
        addUserRole(UserRoles.RESEARCHER.getRoleId(), user.getDacUserId());
        addUserRole(UserRoles.DATAOWNER.getRoleId(), user.getDacUserId());
        User user1 = userDAO.findUserByEmail(user.getEmail());
        assertNotNull(user1);

        // Assert roles are fetched correctly
        assertTrue(user1.getRoles().stream()
            .anyMatch(r -> r.getRoleId().equals(UserRoles.ALUMNI.getRoleId())));
        assertTrue(user1.getRoles().stream()
            .anyMatch(r -> r.getRoleId().equals(UserRoles.ADMIN.getRoleId())));
        assertTrue(user1.getRoles().stream()
            .anyMatch(r -> r.getRoleId().equals(UserRoles.RESEARCHER.getRoleId())));
        assertTrue(user1.getRoles().stream()
            .anyMatch(r -> r.getRoleId().equals(UserRoles.DATAOWNER.getRoleId())));

        User user2 = userDAO.findUserByEmail("no.one@nowhere.com");
        Assert.assertNull(user2);
    }

    @Test
    public void testInsertDACUser() {
        // No-op ... tested in `createUser()`
    }

    @Test
    public void testUpdateDACUser_case1() {
        User user = createUser();
        Institution firstInstitute = createInstitution();
        String newEmail = getRandomEmailAddress();
        userDAO.updateUser(
                "Dac User Test",
                user.getDacUserId(),
                newEmail,
                firstInstitute.getId()
                );
        User user2 = userDAO.findUserById(user.getDacUserId());
        assertEquals(user2.getAdditionalEmail(), newEmail);
        assertEquals(user2.getInstitution().getId(), firstInstitute.getId());
    }

    @Test
    public void testDeleteDACUserById() {
        // No-op ... tested in `tearDown()`
    }

    @Test
    public void testFindUsers_noArgs() {
        createUser();
        Collection<User> users = userDAO.findUsers();
        assertNotNull(users);
        assertFalse(users.isEmpty());
    }

    @Test
    public void testFindUsersWithProfileCompleted() {
        User u = createUser();
        UserProperty p = new UserProperty();
        p.setPropertyKey(UserFields.COMPLETED.getValue());
        p.setPropertyValue("true");
        p.setUserId(u.getDacUserId());
        userPropertyDAO.insertAll(Collections.singletonList(p));
        List<User> users = new ArrayList<>(userDAO.findUsers());
        assertNotNull(users);
        assertFalse(users.isEmpty());
        assertEquals(1, users.size());
        assertTrue(users.get(0).getProfileCompleted());
    }

    @Test
    public void testVerifyAdminUsers() {
        createUserWithRole(UserRoles.ADMIN.getRoleId());
        Integer count = userDAO.verifyAdminUsers();
        assertNotNull(count);
        assertTrue(count > 0);
    }

    @Test
    public void testDescribeUsersByRoleAndEmailPreference() {
        User researcher = createUserWithRole(UserRoles.RESEARCHER.getRoleId());
        userDAO.updateEmailPreference(true, researcher.getDacUserId());
        Collection<User> researchers = userDAO.describeUsersByRoleAndEmailPreference("Researcher", true);
        assertFalse(researchers.isEmpty());

        User owner = createUserWithRole(UserRoles.DATAOWNER.getRoleId());
        userDAO.updateEmailPreference(false, owner.getDacUserId());
        Collection<User> dataOwners = userDAO.describeUsersByRoleAndEmailPreference("DataOwner", false);
        assertFalse(dataOwners.isEmpty());
    }

    @Test
    public void testUpdateEmailPreference() {
        // No-op ... tested in `testDescribeUsersByRoleAndEmailPreference()`
    }

    @Test
    public void testUpdateUserStatus() {
        User user = createUser();
        Integer roleStatusId = RoleStatus.getValueByStatus(RoleStatus.APPROVED.name());
        String roleStatusName = getStatusByValue(roleStatusId);
        userDAO.updateUserStatus(roleStatusId, user.getDacUserId());
        User user2 = userDAO.findUserById(user.getDacUserId());
        assertNotNull(user2);
        assertEquals(roleStatusName, user2.getStatus());
    }

    @Test
    public void testUpdateUserRationale() {
        User user = createUser();
        String rationale = "New Rationale";
        userDAO.updateUserRationale(rationale, user.getDacUserId());
        User user2 = userDAO.findUserById(user.getDacUserId());
        assertNotNull(user2);
        assertEquals(user2.getRationale(), rationale);
    }

    @Test
    public void testFindDACUserByEmailAndRoleId() {
        User chair = createUserWithRole(UserRoles.CHAIRPERSON.getRoleId());
        User user = userDAO.findUserByEmailAndRoleId(chair.getEmail(), UserRoles.CHAIRPERSON.getRoleId());
        assertNotNull(user);
        assertEquals(chair.getDacUserId(), user.getDacUserId());
        assertEquals(chair.getDisplayName(), user.getDisplayName());
    }

    @Test
    public void testFindUsersForElectionsByRoles() {
        DataSet dataset = createDataset();
        Dac dac = createDac();
        User user = createUserWithRoleInDac(UserRoles.CHAIRPERSON.getRoleId(), dac.getDacId());
        Consent consent = createConsent(dac.getDacId());
        Election election = createAccessElection(consent.getConsentId(), dataset.getDataSetId());
        createDacVote(user.getDacUserId(), election.getElectionId());

        Set<User> users = userDAO.findUsersForElectionsByRoles(
                Collections.singletonList(election.getElectionId()),
                Collections.singletonList(UserRoles.CHAIRPERSON.getRoleName()));
        assertNotNull(users);
        assertFalse(users.isEmpty());
        assertEquals(1, users.size());
    }

    @Test
    public void testFindUsersForElectionsByRolesNotFound() {
        DataSet dataset = createDataset();
        Dac dac = createDac();
        User user = createUserWithRoleInDac(UserRoles.MEMBER.getRoleId(), dac.getDacId());
        Consent consent = createConsent(dac.getDacId());
        Election election = createAccessElection(consent.getConsentId(), dataset.getDataSetId());
        createDacVote(user.getDacUserId(), election.getElectionId());

        Set<User> users = userDAO.findUsersForElectionsByRoles(
                Collections.singletonList(election.getElectionId()),
                Collections.singletonList(UserRoles.CHAIRPERSON.getRoleName()));
        assertNotNull(users);
        assertTrue(users.isEmpty());
    }

    @Test
    public void testFindUsersForDatasetsByRole() {
        DataSet dataset = createDataset();
        Dac dac = createDac();
        User user = createUserWithRoleInDac(UserRoles.CHAIRPERSON.getRoleId(), dac.getDacId());
        Consent consent = createConsent(dac.getDacId());
        createAssociation(consent.getConsentId(), dataset.getDataSetId());

        Set<User> users = userDAO.findUsersForDatasetsByRole(
                Collections.singletonList(dataset.getDataSetId()),
                Collections.singletonList(UserRoles.CHAIRPERSON.getRoleName()));
        Optional<User> foundUser = users.stream().findFirst();
        assertNotNull(users);
        assertFalse(users.isEmpty());
        assertEquals(1, users.size());
        assertEquals(user.getDacUserId(), foundUser.get().getDacUserId());
    }

    @Test
    public void testFindUsersForDatasetsByRoleNotFound() {
        DataSet dataset = createDataset();
        Dac dac = createDac();
        createUserWithRoleInDac(UserRoles.MEMBER.getRoleId(), dac.getDacId());
        Consent consent = createConsent(dac.getDacId());
        createAssociation(consent.getConsentId(), dataset.getDataSetId());

        Set<User> users = userDAO.findUsersForDatasetsByRole(
                Collections.singletonList(dataset.getDataSetId()),
                Collections.singletonList(UserRoles.CHAIRPERSON.getRoleName()));
        assertNotNull(users);
        assertTrue(users.isEmpty());
    }

    @Test
    public void testFindUsersByInstitution() {
        Integer institutionId = createUserWithInstitution().getInstitutionId();
        List<User> beforeList = userDAO.findUsersByInstitution(institutionId);
        //should not change results since they are not in the institution
        createUser();
        createUser();
        List<User> afterList = userDAO.findUsersByInstitution(institutionId);
        assertEquals(1, beforeList.size());
        assertEquals(beforeList, afterList);
    }

    @Test
    public void testGetSOsByInstitution() {
        //user with institutionId and SO role
        User user = createUserWithInstitution();
        Integer institutionId = user.getInstitutionId();
        String displayName = user.getDisplayName();
        String email = user.getEmail();
        List<User> users = userDAO.getSOsByInstitution( institutionId);
        assertEquals(1, users.size());
        assertEquals(displayName, users.get(0).getDisplayName());
        assertEquals(email, users.get(0).getEmail());

        List<User> differentInstitutionUsers = userDAO.getSOsByInstitution( institutionId + 1);
        assertEquals(0, differentInstitutionUsers.size());
    }

    private String getRandomEmailAddress() {
        String user = RandomStringUtils.randomAlphanumeric(20);
        String domain = RandomStringUtils.randomAlphanumeric(10);
        return user + "@" + domain + ".org";
    }
}
