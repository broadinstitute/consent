package org.broadinstitute.consent.http.service.users;


import java.util.List;
import java.util.Map;
import javax.ws.rs.NotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.consent.http.db.UserDAO;
import org.broadinstitute.consent.http.db.UserRoleDAO;
import org.broadinstitute.consent.http.enumeration.RoleStatus;
import org.broadinstitute.consent.http.enumeration.UserRoles;
import org.broadinstitute.consent.http.models.User;
import org.broadinstitute.consent.http.service.UserService;
import org.broadinstitute.consent.http.service.users.handler.UserRolesHandler;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @deprecated Use UserService
 * Implementation class for DACUserAPI on top of DACUserDAO database support.
 */
@Deprecated
public class DatabaseDACUserAPI extends AbstractDACUserAPI {

    protected final UserDAO userDAO;
    protected final UserRoleDAO userRoleDAO;
    private final UserService userService;

    public static void initInstance(UserDAO userDao, UserRoleDAO userRoleDAO, UserService userService) {
        DACUserAPIHolder.setInstance(new DatabaseDACUserAPI(userDao, userRoleDAO, userService));
    }

    protected Logger logger() {
        return LoggerFactory.getLogger(this.getClass());
    }

    /**
     * The constructor is private to force use of the factory methods and
     * enforce the singleton pattern.
     *
     * @param userDAO The Data Access Object used to read/write data.
     */
    DatabaseDACUserAPI(UserDAO userDAO, UserRoleDAO userRoleDAO, UserService userService) {
        this.userDAO = userDAO;
        this.userRoleDAO = userRoleDAO;
        this.userService = userService;
    }

    @Override
    public List<User> describeAdminUsersThatWantToReceiveMails() {
        return userDAO.describeUsersByRoleAndEmailPreference(UserRoles.ADMIN.getRoleName(), true);
    }

    @Override
    public User updateUserStatus(String status, Integer userId) {
        Integer statusId = RoleStatus.getValueByStatus(status);
        validateExistentUserById(userId);
        if (statusId == null) {
            throw new IllegalArgumentException(status + " is not a valid status.");
        }
        userDAO.updateUserStatus(statusId, userId);
        return userService.findUserById(userId);
    }

    @Override
    public User updateUserRationale(String rationale, Integer userId) {
        validateExistentUserById(userId);
        if (rationale == null) {
            throw new IllegalArgumentException("Rationale is required.");
        }
        userDAO.updateUserRationale(rationale, userId);
        return userService.findUserById(userId);
    }

    @Override
    public User updateDACUserById(Map<String, User> dac, Integer id) throws IllegalArgumentException, NotFoundException {
        User updatedUser = dac.get(UserRolesHandler.UPDATED_USER_KEY);
        // validate user exists
        validateExistentUserById(id);
        // validate required fields are not null or empty
        validateRequiredFields(updatedUser);
        try {
            userDAO.updateUser(updatedUser.getEmail(), updatedUser.getDisplayName(), id, updatedUser.getAdditionalEmail());
        } catch (UnableToExecuteStatementException e) {
            throw new IllegalArgumentException("Email shoud be unique.");
        }
        User user = userService.findUserByEmail(updatedUser.getEmail());
        user.setRoles(userRoleDAO.findRolesByUserId(user.getDacUserId()));
        return user;
    }

    @Override
    public void updateEmailPreference(boolean preference, Integer userId) {
        userDAO.updateEmailPreference(preference, userId);
    }

    private void validateExistentUserById(Integer id) {
        if (userDAO.findUserById(id) == null) {
            throw new NotFoundException("The user for the specified id does not exist");
        }
    }

    private void validateRequiredFields(User newDac) {
        if (StringUtils.isEmpty(newDac.getDisplayName())) {
            throw new IllegalArgumentException("Display Name can't be null. The user needs a name to display.");
        }
        if (StringUtils.isEmpty(newDac.getEmail())) {
            throw new IllegalArgumentException("The user needs a valid email to be able to login.");
        }
    }

}
