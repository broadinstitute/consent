package org.broadinstitute.consent.http.service.users;

import freemarker.template.TemplateException;
import org.broadinstitute.consent.http.models.DACUser;
import org.broadinstitute.consent.http.service.users.handler.UserRoleHandlerException;

import javax.mail.MessagingException;
import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @deprecated Use UserService
 */
@Deprecated
public interface DACUserAPI {

    DACUser createDACUser(DACUser dacUser) throws IllegalArgumentException;

    List<DACUser> describeAdminUsersThatWantToReceiveMails();

    DACUser updateDACUserById(Map<String, DACUser> dac, Integer userId) throws IllegalArgumentException, NotFoundException, UserRoleHandlerException, MessagingException, IOException, TemplateException;

    DACUser updateUserStatus(String status, Integer userId);

    DACUser updateUserRationale(String rationale, Integer userId);

    void updateEmailPreference(boolean preference, Integer userId);

}
