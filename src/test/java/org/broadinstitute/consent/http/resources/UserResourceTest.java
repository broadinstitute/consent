package org.broadinstitute.consent.http.resources;

import com.google.api.client.http.HttpStatusCodes;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.apache.commons.lang3.RandomStringUtils;
import org.broadinstitute.consent.http.authentication.GoogleUser;
import org.broadinstitute.consent.http.enumeration.UserFields;
import org.broadinstitute.consent.http.enumeration.UserRoles;
import org.broadinstitute.consent.http.models.AuthUser;
import org.broadinstitute.consent.http.models.LibraryCard;
import org.broadinstitute.consent.http.models.UserProperty;
import org.broadinstitute.consent.http.models.User;
import org.broadinstitute.consent.http.models.UserRole;
import org.broadinstitute.consent.http.service.LibraryCardService;
import org.broadinstitute.consent.http.service.UserService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class UserResourceTest {

  @Mock private UserService userService;

  @Mock private LibraryCardService libraryCardService;

  private UserResource userResource;

  @Mock private UriInfo uriInfo;

  @Mock private UriBuilder uriBuilder;

  private final String TEST_EMAIL = "test@gmail.com";

  private AuthUser authUser;

  @Before
  public void setUp() throws URISyntaxException {
    GoogleUser googleUser = new GoogleUser();
    googleUser.setName("Test User");
    googleUser.setEmail(TEST_EMAIL);
    authUser = new AuthUser(googleUser);
    MockitoAnnotations.initMocks(this);
    when(uriInfo.getRequestUriBuilder()).thenReturn(uriBuilder);
    when(uriBuilder.path(anyString())).thenReturn(uriBuilder);
    when(uriBuilder.build(anyString())).thenReturn(new URI("http://localhost:8180/dacuser/api"));
  }

  private void initResource() {
    userResource = new UserResource(userService, libraryCardService);
  }

  @Test
  public void testGetMe() {
    User user = createUserWithRole();
    when(userService.findUserByEmail(any())).thenReturn(user);
    when(userService.findAllUserProperties(any())).thenReturn(createResearcherProperties());
    when(libraryCardService.findLibraryCardsByUserId(any()))
        .thenReturn(createLibraryCards());
    initResource();

    Response response = userResource.getUser(authUser);
    assertEquals(Status.OK.getStatusCode(), response.getStatus());
  }

  @Test
  public void testGetUserById() {
    User user = createUserWithRole();
    when(userService.findUserById(any())).thenReturn(user);
    when(userService.findAllUserProperties(any())).thenReturn(createResearcherProperties());
    when(libraryCardService.findLibraryCardsByUserId(any()))
        .thenReturn(createLibraryCards());;
    initResource();

    Response response = userResource.getUserById(authUser, 1);
    assertEquals(Status.OK.getStatusCode(), response.getStatus());
  }

  @Test
  public void testGetUserByIdNotFound() {
    when(userService.findUserById(any())).thenThrow(new NotFoundException());
    initResource();

    Response response = userResource.getUserById(authUser, 1);
    assertEquals(Status.NOT_FOUND.getStatusCode(), response.getStatus());
  }

  @Test
  public void testGetUsers_SO() {
    User user = createUserWithRole();
    user.setRoles(Arrays.asList(new UserRole(UserRoles.SIGNINGOFFICIAL.getRoleId(), UserRoles.SIGNINGOFFICIAL.getRoleName())));
    when(userService.findUserByEmail(any())).thenReturn(user);
    when(userService.getUsersByUserRole(user, "SigningOfficial")).thenReturn(Arrays.asList(new User(), new User()));
    initResource();

    Response response = userResource.getUsers(authUser, "SigningOfficial");
    assertEquals(Status.OK.getStatusCode(), response.getStatus());
  }

  @Test
  public void testGetUsers_SO_NoRole() {
    User user = createUserWithRole();
    when(userService.findUserByEmail(any())).thenReturn(user);
    initResource();

    Response response = userResource.getUsers(authUser, "SigningOfficial");
    assertEquals(Status.NOT_FOUND.getStatusCode(), response.getStatus());
  }

  @Test
  public void testGetUsers_Admin() {
    User user = createUserWithRole();
    user.setRoles(Arrays.asList(new UserRole(UserRoles.ADMIN.getRoleId(), UserRoles.ADMIN.getRoleName())));
    when(userService.findUserByEmail(any())).thenReturn(user);
    when(userService.getUsersByUserRole(user, "Admin")).thenReturn(Arrays.asList(new User(), new User()));
    initResource();

    Response response = userResource.getUsers(authUser, "Admin");
    assertEquals(Status.OK.getStatusCode(), response.getStatus());
  }

  @Test
  public void testGetUsers_Admin_NoRole() {
    User user = createUserWithRole();
    when(userService.findUserByEmail(any())).thenReturn(user);
    initResource();

    Response response = userResource.getUsers(authUser, "Admin");
    assertEquals(Status.NOT_FOUND.getStatusCode(), response.getStatus());
  }

  @Test
  public void testGetUsers_UnsupportedRole() {
      User user = createUserWithRole();
      when(userService.findUserByEmail(any())).thenReturn(user);
      initResource();

      Response response = userResource.getUsers(authUser, "Researcher");
      assertEquals(Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

  @Test
  public void testGetUsers_InvalidRole() {
    User user = createUserWithRole();
    when(userService.findUserByEmail(any())).thenReturn(user);
    initResource();

    Response response = userResource.getUsers(authUser, "BadRequest");
    assertEquals(Status.BAD_REQUEST.getStatusCode(), response.getStatus());
  }

  @Test
  public void testGetUsers_UserNotFound() {
    when(userService.findUserByEmail(any())).thenThrow(new NotFoundException());
    initResource();

    Response response = userResource.getUsers(authUser, "Admin");
    assertEquals(Status.NOT_FOUND.getStatusCode(), response.getStatus());
  }

  @Test
  public void testCreateExistingUser() {
    User user = new User();
    user.setEmail(TEST_EMAIL);
    List<UserRole> roles = new ArrayList<>();
    UserRole admin = new UserRole();
    admin.setName(UserRoles.ADMIN.getRoleName());
    UserRole researcher = new UserRole();
    researcher.setName(UserRoles.RESEARCHER.getRoleName());
    roles.add(researcher);
    roles.add(admin);
    user.setRoles(roles);
    when(userService.findUserByEmail(user.getEmail())).thenReturn(user);
    initResource();

    Response response = userResource.createResearcher(uriInfo, authUser);
    Assert.assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
  }

  @Test
  public void testCreateFailingGoogleIdentity() {
    User user = new User();
    user.setEmail(TEST_EMAIL);
    initResource();

    Response response = userResource.createResearcher(uriInfo, new AuthUser(TEST_EMAIL));
    Assert.assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
  }

  @Test
  public void createUserSuccess() {
    User user = new User();
    user.setDisplayName("Test");
    user.setEmail(TEST_EMAIL);
    UserRole researcher = new UserRole();
    List<UserRole> roles = new ArrayList<>();
    researcher.setName(UserRoles.RESEARCHER.getRoleName());
    roles.add(researcher);
    user.setRoles(roles);
    when(userService.findUserByEmail(any())).thenThrow(new NotFoundException());
    when(userService.createUser(user)).thenReturn(user);
    initResource();

    Response response = userResource.createResearcher(uriInfo, authUser);
    Assert.assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
  }

  @Test
  public void testDeleteUser() {
    doNothing().when(userService).deleteUserByEmail(any());
    initResource();
    Response response = userResource.delete(RandomStringUtils.random(10), uriInfo);
    assertEquals(200, response.getStatus());
  }

  @Test
  public void testAddRoleToUser() {
    User user = createUserWithRole();
    when(userService.findUserById(any())).thenReturn(user);
    when(userService.findAllUserProperties(any())).thenReturn(createResearcherProperties());
    when(libraryCardService.findLibraryCardsByUserId(any()))
        .thenReturn(createLibraryCards());
    initResource();
    Response response = userResource.addRoleToUser(authUser, 1, UserRoles.ADMIN.getRoleId());
    assertEquals(200, response.getStatus());
  }

  @Test
  public void testAddRoleToUserNotFound() {
    doThrow(new NotFoundException()).when(userService).findUserById(any());
    initResource();
    Response response = userResource.addRoleToUser(authUser, 1, UserRoles.ADMIN.getRoleId());
    assertEquals(404, response.getStatus());
  }

  @Test
  public void testAddRoleToUserNotModified() {
    User user = createUserWithRole();
    when(userService.findUserById(any())).thenReturn(user);
    when(userService.findAllUserProperties(any())).thenReturn(createResearcherProperties());
    when(libraryCardService.findLibraryCardsByUserId(any()))
        .thenReturn(createLibraryCards());
    initResource();
    Response response = userResource.addRoleToUser(authUser, 1, UserRoles.RESEARCHER.getRoleId());
    assertEquals(304, response.getStatus());
  }

  @Test
  public void testAddRoleToUserBadRequest() {
    User user = createUserWithRole();
    when(userService.findUserById(any())).thenReturn(user);
    when(userService.findAllUserProperties(any())).thenReturn(createResearcherProperties());
    when(libraryCardService.findLibraryCardsByUserId(any()))
        .thenReturn(createLibraryCards());
    initResource();
    Response response = userResource.addRoleToUser(authUser, 1, 1000);
    assertEquals(400, response.getStatus());
  }

  @Test
  public void testGetSOsForInstitution() {
    User user = createUserWithInstitution();
    User so = createUserWithRole();
    when(userService.findUserByEmail(any())).thenReturn(user);
    when(userService.findSOsByInstitutionId(any())).thenReturn(Arrays.asList(new UserService.SimplifiedUser(so), new UserService.SimplifiedUser(so)));
    initResource();
    Response response = userResource.getSOsForInstitution(authUser);
    assertEquals(HttpStatusCodes.STATUS_CODE_OK, response.getStatus());
  }

  @Test
  public void testGetSOsForInstitution_NoInstitution() {
    User user = createUserWithRole();
    User so = createUserWithRole();
    when(userService.findUserByEmail(any())).thenReturn(user);
    when(userService.findSOsByInstitutionId(any())).thenReturn(Arrays.asList(new UserService.SimplifiedUser(so), new UserService.SimplifiedUser(so)));
    initResource();
    Response response = userResource.getSOsForInstitution(authUser);
    assertEquals(HttpStatusCodes.STATUS_CODE_NOT_FOUND, response.getStatus());
  }

  @Test
  public void testGetSOsForInstitution_UserNotFound() {
    when(userService.findUserByEmail(any())).thenThrow(new NotFoundException());
    initResource();
    Response response = userResource.getSOsForInstitution(authUser);
    assertEquals(HttpStatusCodes.STATUS_CODE_NOT_FOUND, response.getStatus());
  }

  private User createUserWithRole() {
    User user = new User();
    user.setDacUserId(1);
    user.setDisplayName("Test");
    user.setEmail("Test");
    UserRole researcher = new UserRole();
    List<UserRole> roles = new ArrayList<>();
    researcher.setName(UserRoles.RESEARCHER.getRoleName());
    researcher.setRoleId(UserRoles.RESEARCHER.getRoleId());
    roles.add(researcher);
    user.setRoles(roles);
    return user;
  }

  private User createUserWithInstitution() {
    User user = new User();
    user.setDacUserId(1);
    user.setDisplayName("Test Name");
    user.setEmail("Test Email");
    user.setInstitutionId(1);
    return user;
  }

  private List<UserProperty> createResearcherProperties() {
    return Arrays.stream(UserFields.values())
        .filter(UserFields::getRequired)
        .map(f -> new UserProperty(1, f.getValue(), f.getValue()))
        .collect(Collectors.toList());
  }

  private List<LibraryCard> createLibraryCards() {
    LibraryCard e = new LibraryCard();
    String randomValue = RandomStringUtils.random(10, true, false);
    e.setEraCommonsId(randomValue);
    e.setUserEmail(randomValue);
    e.setUserName(randomValue);
    e.setInstitutionId(new Random().nextInt());
    return Collections.singletonList(e);
  }
}
