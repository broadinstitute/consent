package org.broadinstitute.consent.http.service;

import org.broadinstitute.consent.http.enumeration.HeaderDAR;
import org.broadinstitute.consent.http.models.DataAccessRequest;
import org.broadinstitute.consent.http.models.DataAccessRequestData;
import org.broadinstitute.consent.http.models.DatasetDetailEntry;
import org.broadinstitute.consent.http.models.Election;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

public class DataAccessReportsParserTest {

    DataAccessReportsParser parser;
    private final String SC_ID = "SC-01253";
    private final String CONSENT_NAME = "ORSP-1903";
    private final String NAME = "Test";
    private final String REQUESTER = "Wesley";
    private final String ORGANIZATION = "Broad";
    private final String RUS_SUMMARY = "Purpose";
    private final String sDUL = "Samples Restricted for use with \"cancer\" [DOID_162(CC)]\n" +
            "Future use by for-profit entities is prohibited [NPU]\n" +
            "Future use of aggregate-level data for general research purposes is prohibited [NPNV]\n" +
            "Notes:\n" +
            "Future use for methods research (analytic/software/technology development) is not prohibited\n" +
            "Future use as a control set for diseases other than those specified is not prohibited";
    private final String DAR_CODE = "DAR_3";
    private final String TRANSLATED_USE_RESTRICTION = "Samples will be used under the following conditions:<br>Data will be used for health/medical/biomedical research <br>Data will be used to study:  kidney-cancer [DOID_263(CC)], kidney-failure [DOID_1074(CC)]<br>Data will be used for commercial purpose [NPU] <br>";
    private final String EMAIL = "vvicario@test.com";

    public DataAccessReportsParserTest() {
        this.parser = new DataAccessReportsParser();
    }

    @Test
    public void testDataAccessApprovedReport() throws IOException {
        File file = File.createTempFile("ApprovedDataAccessRequests.tsv", ".tsv");
        Date currentDate = new Date();
        Election election = createElection(currentDate);
        DataAccessRequest dar = createDAR(currentDate);
        FileWriter darWriter = new FileWriter(file);
        parser.setApprovedDARHeader(darWriter);
        parser.addApprovedDARLine(darWriter, election, dar, REQUESTER, ORGANIZATION, CONSENT_NAME, sDUL);
        darWriter.flush();
        Stream<String> stream = Files.lines(Paths.get(file.getPath()));
        Iterator<String> iterator = stream.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            String line = iterator.next();
            String[] columns = line.split("\t");
            assertTrue(columns.length == 12);
            if(i == 0) {
                assertTrue(columns[0].equals(HeaderDAR.DAR_ID.getValue()));
                assertTrue(columns[1].equals(HeaderDAR.DATASET_NAME.getValue()));
                assertTrue(columns[2].equals(HeaderDAR.DATASET_ID.getValue()));
                assertTrue(columns[3].equals(HeaderDAR.CONSENT_ID.getValue()));
                assertTrue(columns[4].equals(HeaderDAR.DATA_REQUESTER_NAME.getValue()));
                assertTrue(columns[5].equals(HeaderDAR.ORGANIZATION.getValue()));
                assertTrue(columns[6].equals(HeaderDAR.CODED_VERSION_SDUL.getValue()));
                assertTrue(columns[7].equals(HeaderDAR.CODED_VERSION_DAR.getValue()));
                assertTrue(columns[8].equals(HeaderDAR.RESEARCH_PURPOSE.getValue()));
                assertTrue(columns[9].equals(HeaderDAR.DATE_REQUEST_SUBMISSION.getValue()));
                assertTrue(columns[10].equals(HeaderDAR.DATE_REQUEST_APPROVAL.getValue()));
                assertTrue(columns[11].equals(HeaderDAR.DATE_REQUEST_RE_ATTESTATION.getValue()));
            }
            if (i == 1) {
                assertTrue(columns[0].equals(DAR_CODE));
                assertTrue(columns[1].equals(NAME));
                assertTrue(columns[2].equals(""));
                assertTrue(columns[3].equals(CONSENT_NAME));
                assertTrue(columns[4].equals(REQUESTER));
                assertTrue(columns[5].equals(ORGANIZATION));
                assertTrue(columns[6].equals(sDUL.replace("\n", " ")));
                assertTrue(columns[7].equals(TRANSLATED_USE_RESTRICTION.replace("<br>"," ")));
                assertTrue(columns[8].equals(RUS_SUMMARY));
            }
            i++;
        }
        assertTrue(i == 2);
    }

    @Test
    public void testDataAccessReviewedReport() throws IOException {
        File file = File.createTempFile("ApprovedDataAccessRequests.tsv", ".tsv");
        Date currentDate = new Date();
        Election election = createElection(currentDate);
        DataAccessRequest dar = createDAR(currentDate);
        FileWriter darWriter = new FileWriter(file);
        parser.setReviewedDARHeader(darWriter);
        parser.addReviewedDARLine(darWriter, election, dar, CONSENT_NAME, sDUL);
        darWriter.flush();
        Stream<String> stream = Files.lines(Paths.get(file.getPath()));
        Iterator<String> iterator = stream.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            String line = iterator.next();
            String[] columns = line.split("\t");
            assertTrue(columns.length == 8);
            if(i == 0) {
                assertTrue(columns[0].equals(HeaderDAR.DAR_ID.getValue()));
                assertTrue(columns[1].equals(HeaderDAR.DATASET_NAME.getValue()));
                assertTrue(columns[2].equals(HeaderDAR.DATASET_ID.getValue()));
                assertTrue(columns[3].equals(HeaderDAR.CONSENT_ID.getValue()));
                assertTrue(columns[4].equals(HeaderDAR.CODED_VERSION_SDUL.getValue()));
                assertTrue(columns[5].equals(HeaderDAR.CODED_VERSION_DAR.getValue()));
                assertTrue(columns[6].equals(HeaderDAR.DATE_REQUEST_APPROVAL_DISAPROVAL.getValue()));
                assertTrue(columns[7].equals(HeaderDAR.APPROVED_DISAPPROVED.getValue()));
            }
            if (i == 1) {
                assertTrue(columns[0].equals(DAR_CODE));
                assertTrue(columns[1].equals(NAME));
                assertTrue(columns[2].equals(""));
                assertTrue(columns[3].equals(CONSENT_NAME));
                assertTrue(columns[4].equals(sDUL.replace("\n", " ")));
                assertTrue(columns[5].equals(TRANSLATED_USE_RESTRICTION.replace("<br>"," ")));
                assertTrue(columns[7].equals("Yes"));
            }
            i++;
        }
        assertTrue(i == 2);
    }

    @Test
    public void testDataSetApprovedUsers() throws IOException{
        File file = File.createTempFile("DataSetApprovedUsers", ".tsv");
        FileWriter darWriter = new FileWriter(file);
        parser.setDataSetApprovedUsersHeader(darWriter);
        Date approvalDate = new Date();
        parser.addDataSetApprovedUsersLine(darWriter, EMAIL, REQUESTER, ORGANIZATION, DAR_CODE, approvalDate);
        darWriter.flush();
        Stream<String> stream = Files.lines(Paths.get(file.getPath()));
        Iterator<String> iterator = stream.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            String line = iterator.next();
            String[] columns = line.split("\t");
            assertTrue(columns.length == 6);
            if(i == 0) {
                assertTrue(columns[0].equals(HeaderDAR.USERNAME.getValue()));
                assertTrue(columns[1].equals(HeaderDAR.NAME.getValue()));
                assertTrue(columns[2].equals(HeaderDAR.ORGANIZATION.getValue()));
                assertTrue(columns[3].equals(HeaderDAR.DAR_ID.getValue()));
                assertTrue(columns[4].equals(HeaderDAR.DATE_REQUEST_APPROVAL.getValue()));
                assertTrue(columns[5].equals(HeaderDAR.RENEWAL_DATE.getValue()));
            }
            if (i == 1) {
                assertTrue(columns[0].equals(EMAIL));
                assertTrue(columns[1].equals(REQUESTER));
                assertTrue(columns[2].equals(ORGANIZATION));
                assertTrue(columns[3].equals(DAR_CODE));
            }
            i++;
        }
    }

    private Election createElection(Date currentDate){
        Election election = new Election();
        election.setFinalVoteDate(currentDate);
        election.setFinalVote(true);
        return election;
    }

    private DataAccessRequest createDAR(Date currentDate) {
        DataAccessRequest dar = new DataAccessRequest();
        DataAccessRequestData data = new DataAccessRequestData();
        DatasetDetailEntry datasetDetail = new DatasetDetailEntry();
        datasetDetail.setObjectId(SC_ID);
        datasetDetail.setName(NAME);
        List<DatasetDetailEntry> detailsList = new ArrayList<>();
        detailsList.add(datasetDetail);
        data.setDatasetDetail(detailsList);
        data.setDatasetIds(new ArrayList<>());
        data.setDarCode(DAR_CODE);
        data.setTranslatedUseRestriction(TRANSLATED_USE_RESTRICTION);
        data.setNonTechRus(RUS_SUMMARY);
        dar.setData(data);
        dar.setSortDate(new Timestamp(currentDate.getTime()));
        return dar;
    }
}
