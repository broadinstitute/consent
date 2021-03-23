package org.broadinstitute.consent.http.service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.ws.rs.NotFoundException;
import org.broadinstitute.consent.http.models.User;
import org.broadinstitute.consent.http.models.darsummary.DARModalDetailsDTO;
import org.bson.Document;

@Deprecated // Use DataAccessRequestService
public interface DataAccessRequestAPI {

    Document describeDataAccessRequestById(String id) throws NotFoundException;

    List<Document> describeDataAccessWithDataSetIdAndRestriction(List<Integer> dataSetIds);

    Document describeDataAccessRequestFieldsById(String id, List<String> fields) throws NotFoundException;

    List<Document> describeDraftDataAccessRequestManage(Integer userId);

    List<User> getUserEmailAndCancelElection(String referenceId);

    File createApprovedDARDocument() throws NotFoundException, IOException;

    File createReviewedDARDocument() throws NotFoundException, IOException;

    File createDataSetApprovedUsersDocument(Integer dataSetId) throws IOException;

    DARModalDetailsDTO DARModalDetailsDTOBuilder(Document dar, User user, ElectionService electionService);

}


