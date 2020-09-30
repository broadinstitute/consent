package org.broadinstitute.consent.http.resources;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.junit.Test;
import org.mockito.Mockito;
import org.owasp.fileio.FileValidator;

public class ResourceTest {

  @Test
  public void testValidateFileDetails() {
    Long maxSize = new FileValidator().getMaxFileUploadSize();
    Resource abstractResource = mock(Resource.class, Mockito.CALLS_REAL_METHODS);
    FormDataContentDisposition fileDetail = mock(FormDataContentDisposition.class);
    when(fileDetail.getFileName()).thenReturn("temp.txt");
    when(fileDetail.getSize()).thenReturn(maxSize);
    try {
      abstractResource.validateFileDetails(fileDetail);
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testValidateFileDetailsFileName() {
    Resource abstractResource = mock(Resource.class, Mockito.CALLS_REAL_METHODS);
    FormDataContentDisposition fileDetail = mock(FormDataContentDisposition.class);
    when(fileDetail.getFileName()).thenReturn("C:\\temp\\virus.exe");
    abstractResource.validateFileDetails(fileDetail);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testValidateFileDetailsFileSize() {
    Long maxSize = new FileValidator().getMaxFileUploadSize();
    Resource abstractResource = mock(Resource.class, Mockito.CALLS_REAL_METHODS);
    FormDataContentDisposition fileDetail = mock(FormDataContentDisposition.class);
    when(fileDetail.getFileName()).thenReturn("temp.txt");
    when(fileDetail.getSize()).thenReturn(maxSize + 1);
    abstractResource.validateFileDetails(fileDetail);
  }

}
