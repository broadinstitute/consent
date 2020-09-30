package org.broadinstitute.consent.http.resources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.cloud.storage.BlobId;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.apache.commons.io.IOUtils;
import org.broadinstitute.consent.http.cloudstore.GCSService;
import org.broadinstitute.consent.http.models.AuthUser;
import org.broadinstitute.consent.http.models.DataAccessRequest;
import org.broadinstitute.consent.http.models.DataAccessRequestData;
import org.broadinstitute.consent.http.models.User;
import org.broadinstitute.consent.http.service.AbstractMatchProcessAPI;
import org.broadinstitute.consent.http.service.DataAccessRequestService;
import org.broadinstitute.consent.http.service.EmailNotifierService;
import org.broadinstitute.consent.http.service.MatchProcessAPI;
import org.broadinstitute.consent.http.service.UserService;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.testcontainers.shaded.org.apache.commons.lang.RandomStringUtils;

@RunWith(PowerMockRunner.class)
@PrepareForTest({AbstractMatchProcessAPI.class})
public class DataAccessRequestResourceVersion2Test {

  @Mock private DataAccessRequestService dataAccessRequestService;
  @Mock private MatchProcessAPI matchProcessAPI;
  @Mock private EmailNotifierService emailNotifierService;
  @Mock private GCSService gcsService;
  @Mock private UserService userService;
  @Mock private UriInfo info;
  @Mock private UriBuilder builder;

  private final AuthUser authUser = new AuthUser("test@test.com");
  private final User user = new User(1, authUser.getName(), "Display Name", new Date());

  private DataAccessRequestResourceVersion2 resource;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    PowerMockito.mockStatic(AbstractMatchProcessAPI.class);
  }

  private void initResource() {
    try {
      when(builder.path(anyString())).thenReturn(builder);
      when(builder.build()).thenReturn(URI.create("https://test.domain.org/some/path"));
      when(info.getRequestUriBuilder()).thenReturn(builder);
      when(AbstractMatchProcessAPI.getInstance()).thenReturn(matchProcessAPI);
      resource =
          new DataAccessRequestResourceVersion2(
              dataAccessRequestService, emailNotifierService, gcsService, userService);
    } catch (Exception e) {
      fail("Initialization Exception: " + e.getMessage());
    }
  }

  @Test
  public void testCreateDataAccessRequest() {
    try {
      when(userService.findUserByEmail(any())).thenReturn(user);
      when(dataAccessRequestService.createDataAccessRequest(any(), any()))
          .thenReturn(Collections.emptyList());
      doNothing().when(matchProcessAPI).processMatchesForPurpose(any());
      doNothing().when(emailNotifierService).sendNewDARRequestMessage(any(), any());
    } catch (Exception e) {
      fail("Initialization Exception: " + e.getMessage());
    }
    initResource();
    Response response = resource.createDataAccessRequest(authUser, info, "");
    assertEquals(201, response.getStatus());
  }

  @Test
  public void testGetByReferenceId() {
    when(dataAccessRequestService.findByReferenceId(any())).thenReturn(generateDataAccessRequest());
    initResource();
    Response response = resource.getByReferenceId(authUser, "");
    assertEquals(200, response.getStatus());
  }

  @Test
  public void testUpdateByReferenceId() {
    DataAccessRequest dar = generateDataAccessRequest();
    try {
      when(userService.findUserByEmail(any())).thenReturn(user);
      when(dataAccessRequestService.findByReferenceId(any())).thenReturn(dar);
      when(dataAccessRequestService.updateByReferenceIdVersion2(any(), any())).thenReturn(dar);
      doNothing().when(matchProcessAPI).processMatchesForPurpose(any());
    } catch (Exception e) {
      fail("Initialization Exception: " + e.getMessage());
    }
    initResource();
    Response response = resource.updateByReferenceId(authUser, "", "{}");
    assertEquals(200, response.getStatus());
  }

  @Test
  public void testUpdateByReferenceIdForbidden() {
    User invalidUser = new User(1000, authUser.getName(), "Display Name", new Date());
    DataAccessRequest dar = generateDataAccessRequest();
    try {
      when(userService.findUserByEmail(any())).thenReturn(invalidUser);
      when(dataAccessRequestService.findByReferenceId(any())).thenReturn(dar);
      when(dataAccessRequestService.updateByReferenceIdVersion2(any(), any())).thenReturn(dar);
      doNothing().when(matchProcessAPI).processMatchesForPurpose(any());
    } catch (Exception e) {
      fail("Initialization Exception: " + e.getMessage());
    }
    initResource();
    Response response = resource.updateByReferenceId(authUser, "", "{}");
    assertEquals(403, response.getStatus());
  }

  @Test
  public void testCreateDraftDataAccessRequest() {
    DataAccessRequest dar = generateDataAccessRequest();
    try {
      when(userService.findUserByEmail(any())).thenReturn(user);
      when(dataAccessRequestService.insertDraftDataAccessRequest(any(), any())).thenReturn(dar);
    } catch (Exception e) {
      fail("Initialization Exception: " + e.getMessage());
    }
    initResource();
    Response response = resource.createDraftDataAccessRequest(authUser, info, "");
    assertEquals(201, response.getStatus());
  }

  @Test
  public void testUpdatePartialDataAccessRequest() {
    DataAccessRequest dar = generateDataAccessRequest();
    when(userService.findUserByEmail(any())).thenReturn(user);
    when(dataAccessRequestService.findByReferenceId(any())).thenReturn(dar);
    when(dataAccessRequestService.updateByReferenceIdVersion2(any(), any())).thenReturn(dar);
    initResource();

    Response response = resource.updatePartialDataAccessRequest(authUser, "", "{}");
    assertEquals(200, response.getStatus());
  }

  @Test
  public void testUpdatePartialDataAccessRequestForbidden() {
    User invalidUser = new User(1000, authUser.getName(), "Display Name", new Date());
    DataAccessRequest dar = generateDataAccessRequest();
    when(userService.findUserByEmail(any())).thenReturn(invalidUser);
    when(dataAccessRequestService.findByReferenceId(any())).thenReturn(dar);
    when(dataAccessRequestService.updateByReferenceIdVersion2(any(), any())).thenReturn(dar);
    initResource();

    Response response = resource.updatePartialDataAccessRequest(authUser, "", "{}");
    assertEquals(403, response.getStatus());
  }

  @Test
  public void testGetIrbDocument() {
    when(userService.findUserByEmail(any())).thenReturn(user);
    DataAccessRequest dar = generateDataAccessRequest();
    dar.getData().setIrbDocumentLocation(RandomStringUtils.random(10));
    dar.getData().setIrbDocumentName(RandomStringUtils.random(10) + ".txt");
    when(dataAccessRequestService.findByReferenceId(any())).thenReturn(dar);
    initResource();

    Response response = resource.getIrbDocument(authUser, "");
    assertEquals(200, response.getStatus());
  }

  @Test
  public void testGetIrbDocumentNotFound() {
    when(userService.findUserByEmail(any())).thenReturn(user);
    when(dataAccessRequestService.findByReferenceId(any())).thenReturn(generateDataAccessRequest());
    initResource();

    Response response = resource.getIrbDocument(authUser, "");
    assertEquals(404, response.getStatus());
  }

  @Test
  public void testGetIrbDocumentDARNotFound() {
    when(userService.findUserByEmail(any())).thenReturn(user);
    when(dataAccessRequestService.findByReferenceId(any())).thenReturn(null);
    initResource();

    Response response = resource.getIrbDocument(authUser, "");
    assertEquals(404, response.getStatus());
  }

  @Test
  public void testUploadIrbDocument() throws Exception {
    when(userService.findUserByEmail(any())).thenReturn(user);
    DataAccessRequest dar = generateDataAccessRequest();
    when(dataAccessRequestService.updateByReferenceIdVersion2(any(), any())).thenReturn(dar);
    when(dataAccessRequestService.findByReferenceId(any())).thenReturn(dar);
    InputStream uploadInputStream = IOUtils.toInputStream("test", Charset.defaultCharset());
    FormDataContentDisposition formData = mock(FormDataContentDisposition.class);
    when(formData.getFileName()).thenReturn("temp.txt");
    when(formData.getType()).thenReturn("txt");
    when(formData.getSize()).thenReturn(1L);
    when(gcsService.storeDocument(any(), any(), any())).thenReturn(BlobId.of("bucket", "name"));
    initResource();

    Response response = resource.uploadIrbDocument(authUser, "", uploadInputStream, formData);
    assertEquals(200, response.getStatus());
  }

  @Test
  public void testUploadIrbDocumentDARNotFound() throws Exception {
    when(userService.findUserByEmail(any())).thenReturn(user);
    when(dataAccessRequestService.findByReferenceId(any())).thenReturn(null);
    InputStream uploadInputStream = IOUtils.toInputStream("test", Charset.defaultCharset());
    FormDataContentDisposition formData = mock(FormDataContentDisposition.class);
    when(formData.getFileName()).thenReturn("temp.txt");
    when(formData.getType()).thenReturn("txt");
    when(formData.getSize()).thenReturn(1L);
    when(gcsService.storeDocument(any(), any(), any())).thenReturn(BlobId.of("bucket", "name"));
    initResource();

    Response response = resource.uploadIrbDocument(authUser, "", uploadInputStream, formData);
    assertEquals(404, response.getStatus());
  }

  @Test
  public void testUploadIrbDocumentWithPreviousIrbDocument() throws Exception {
    when(userService.findUserByEmail(any())).thenReturn(user);
    DataAccessRequest dar = generateDataAccessRequest();
    dar.getData().setIrbDocumentLocation(RandomStringUtils.random(10));
    dar.getData().setIrbDocumentName(RandomStringUtils.random(10) + ".txt");
    when(dataAccessRequestService.updateByReferenceIdVersion2(any(), any())).thenReturn(dar);
    when(dataAccessRequestService.findByReferenceId(any())).thenReturn(dar);
    InputStream uploadInputStream = IOUtils.toInputStream("test", Charset.defaultCharset());
    FormDataContentDisposition formData = mock(FormDataContentDisposition.class);
    when(formData.getFileName()).thenReturn("temp.txt");
    when(formData.getType()).thenReturn("txt");
    when(formData.getSize()).thenReturn(1L);
    when(gcsService.storeDocument(any(), any(), any())).thenReturn(BlobId.of("bucket", "name"));
    when(gcsService.deleteDocument(any())).thenReturn(true);
    initResource();

    Response response = resource.uploadIrbDocument(authUser, "", uploadInputStream, formData);
    assertEquals(200, response.getStatus());
  }

  @Test
  public void testGetCollaborationDocument() {
    when(userService.findUserByEmail(any())).thenReturn(user);
    DataAccessRequest dar = generateDataAccessRequest();
    dar.getData().setCollaborationLetterLocation(RandomStringUtils.random(10));
    dar.getData().setCollaborationLetterName(RandomStringUtils.random(10) + ".txt");
    when(dataAccessRequestService.findByReferenceId(any())).thenReturn(dar);
    initResource();

    Response response = resource.getCollaborationDocument(authUser, "");
    assertEquals(200, response.getStatus());
  }

  @Test
  public void testGetCollaborationDocumentNotFound() {
    when(userService.findUserByEmail(any())).thenReturn(user);
    when(dataAccessRequestService.findByReferenceId(any())).thenReturn(generateDataAccessRequest());
    initResource();

    Response response = resource.getCollaborationDocument(authUser, "");
    assertEquals(404, response.getStatus());
  }

  @Test
  public void testGetCollaborationDocumentDARNotFound() {
    when(userService.findUserByEmail(any())).thenReturn(user);
    when(dataAccessRequestService.findByReferenceId(any())).thenReturn(null);
    initResource();

    Response response = resource.getCollaborationDocument(authUser, "");
    assertEquals(404, response.getStatus());
  }

  @Test
  public void testUploadCollaborationDocument() throws Exception {
    when(userService.findUserByEmail(any())).thenReturn(user);
    DataAccessRequest dar = generateDataAccessRequest();
    when(dataAccessRequestService.updateByReferenceIdVersion2(any(), any())).thenReturn(dar);
    when(dataAccessRequestService.findByReferenceId(any())).thenReturn(dar);
    InputStream uploadInputStream = IOUtils.toInputStream("test", Charset.defaultCharset());
    FormDataContentDisposition formData = mock(FormDataContentDisposition.class);
    when(formData.getFileName()).thenReturn("temp.txt");
    when(formData.getType()).thenReturn("txt");
    when(formData.getSize()).thenReturn(1L);
    when(gcsService.storeDocument(any(), any(), any())).thenReturn(BlobId.of("buket", "name"));
    initResource();

    Response response = resource.uploadCollaborationDocument(authUser, "", uploadInputStream, formData);
    assertEquals(200, response.getStatus());
  }

  @Test
  public void testUploadCollaborationDocumentDARNotFound() throws Exception {
    when(userService.findUserByEmail(any())).thenReturn(user);
    when(dataAccessRequestService.findByReferenceId(any())).thenReturn(null);
    InputStream uploadInputStream = IOUtils.toInputStream("test", Charset.defaultCharset());
    FormDataContentDisposition formData = mock(FormDataContentDisposition.class);
    when(formData.getFileName()).thenReturn("temp.txt");
    when(formData.getType()).thenReturn("txt");
    when(formData.getSize()).thenReturn(1L);
    when(gcsService.storeDocument(any(), any(), any())).thenReturn(BlobId.of("bucket", "name"));
    initResource();

    Response response = resource.uploadCollaborationDocument(authUser, "", uploadInputStream, formData);
    assertEquals(404, response.getStatus());
  }

  @Test
  public void testUploadCollaborationDocumentWithPreviousDocument() throws Exception {
    when(userService.findUserByEmail(any())).thenReturn(user);
    DataAccessRequest dar = generateDataAccessRequest();
    dar.getData().setCollaborationLetterLocation(RandomStringUtils.random(10));
    dar.getData().setCollaborationLetterName(RandomStringUtils.random(10) + ".txt");
    when(dataAccessRequestService.updateByReferenceIdVersion2(any(), any())).thenReturn(dar);
    when(dataAccessRequestService.findByReferenceId(any())).thenReturn(dar);
    InputStream uploadInputStream = IOUtils.toInputStream("test", Charset.defaultCharset());
    FormDataContentDisposition formData = mock(FormDataContentDisposition.class);
    when(formData.getFileName()).thenReturn("temp.txt");
    when(formData.getType()).thenReturn("txt");
    when(formData.getSize()).thenReturn(1L);
    when(gcsService.storeDocument(any(), any(), any())).thenReturn(BlobId.of("bucket", "name"));
    when(gcsService.deleteDocument(any())).thenReturn(true);
    initResource();

    Response response = resource.uploadCollaborationDocument(authUser, "", uploadInputStream, formData);
    assertEquals(200, response.getStatus());
  }

  private DataAccessRequest generateDataAccessRequest() {
    Timestamp now = new Timestamp(new Date().getTime());
    DataAccessRequest dar = new DataAccessRequest();
    DataAccessRequestData data = new DataAccessRequestData();
    dar.setReferenceId(UUID.randomUUID().toString());
    data.setReferenceId(dar.getReferenceId());
    data.setDatasetIds(Arrays.asList(1, 2));
    dar.setData(data);
    dar.setUserId(user.getDacUserId());
    dar.setCreateDate(now);
    dar.setUpdateDate(now);
    dar.setSortDate(now);
    return dar;
  }
}
