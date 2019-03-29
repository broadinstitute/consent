package org.broadinstitute.consent.http.resources;

import com.mongodb.MongoClient;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.broadinstitute.consent.http.ConsentApplication;
import org.broadinstitute.consent.http.configurations.ConsentConfiguration;
import org.broadinstitute.consent.http.db.mongo.MongoConsentDB;
import org.broadinstitute.consent.http.models.DataAccessRequestManage;
import org.broadinstitute.consent.http.models.dto.UseRestrictionDTO;
import org.broadinstitute.consent.http.service.DatabaseDataAccessRequestAPI;
import org.broadinstitute.consent.http.util.DarConstants;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;

public class DataAccessRequestResourceTest extends DataAccessRequestServiceTest {

    private final static Logger log = LoggerFactory.getLogger(DataAccessRequestResourceTest.class);
    private static final String TEST_DATABASE_NAME = "TestConsent";

    @ClassRule
    public static final DropwizardAppRule<ConsentConfiguration> RULE = new DropwizardAppRule<>(
            ConsentApplication.class, resourceFilePath("consent-config.yml"));

    @Override
    public DropwizardAppRule<ConsentConfiguration> rule() {
        return RULE;
    }

    @Before
    public void setUp() throws IOException {
        MongoClient mongo = setUpMongoClient();
        MongoConsentDB mongoi = new MongoConsentDB(mongo, TEST_DATABASE_NAME);
        MongoConsentDB spiedMongoi = Mockito.spy(mongoi);
        DatabaseDataAccessRequestAPI.getInstance().setMongoDBInstance(spiedMongoi);
        doReturn("TESTDAR-COUNTER").when(spiedMongoi).getNextSequence(DarConstants.PARTIAL_DAR_CODE_COUNTER);
        mongoi.getDataAccessRequestCollection().drop();
        mongoi.getPartialDataAccessRequestCollection().drop();
    }

    @After
    public void teardown() {
        shutDownMongo();
    }


    @Ignore
    @Test
    public void testDarOperations() throws IOException {
        Client client = ClientBuilder.newClient();
        Document sampleDar = DataRequestSamplesHolder.getSampleDar();
        long  mongoDocuments = retrieveDars(client, darPath()).size();
        checkStatus(CREATED, post(client, darPath(), sampleDar));
        List<Document> created = retrieveDars(client, darPath());
        assertTrue(created.size() == ++mongoDocuments);
    }

    @Ignore
    @Test
    public void testPartialDarOperations() throws IOException {
        Client client = ClientBuilder.newClient();
        int partialDocumentsCount = retrieveDars(client, partialsPath()).size();
        Document partialDar = DataRequestSamplesHolder.getSampleDar();
        checkStatus(CREATED, post(client, partialPath(), partialDar));
        List<Document> created = retrieveDars(client, partialsPath());
        assertTrue(created.size() == ++partialDocumentsCount);
    }

    @Ignore
    @Test
    public void testInvalidDars() throws IOException {
        Client client = ClientBuilder.newClient();
        List<UseRestrictionDTO> dtos = getJson(client, invalidDarsPath()).readEntity(new GenericType<List<UseRestrictionDTO>>() {});
        assertTrue(dtos.size() == 0);
    }


    @Ignore
    @Test
    public void testManageDars() throws IOException {
        Client client = ClientBuilder.newClient();
        Document sampleDar = DataRequestSamplesHolder.getSampleDar();
        checkStatus(CREATED, post(client, darPath(), sampleDar));
        List<DataAccessRequestManage> manageDars = getJson(client, darManagePath("1")).readEntity(new GenericType<List<DataAccessRequestManage>>() {
        });
        assertTrue(manageDars.size() == 1);

    }

    @Ignore
    @Test
    public void testManagePartialDars() throws IOException {
        Client client = ClientBuilder.newClient();
        Document partialDar = DataRequestSamplesHolder.getSampleDar();
        checkStatus(CREATED, post(client, partialPath(), partialDar));
        List<Document> manageDars = getJson(client, partialsManagePath("1")).readEntity(new GenericType<List<Document>>() {
        });
        assertTrue(manageDars.size() == 1);
    }

    @Ignore
    @Test
    public void testRestrictionFromQuestions() throws IOException {
        Client client = ClientBuilder.newClient();
        Response response = checkStatus(OK, put(client, restrictionFromQuestionsUrl(), DataRequestSamplesHolder.getSampleDar()));
        String res = response.readEntity(String.class);
        assertTrue(res.equals("{\"useRestriction\":\"Manual Review\"}"));
    }

    @Test
    public void testDarFound() throws Exception {
        // Create a DAR
        Client client = ClientBuilder.newClient();
        Document sampleDar = DataRequestSamplesHolder.getSampleDar();
        checkStatus(CREATED, post(client, darPath(), sampleDar));
        List<Document> created = retrieveDars(client, darPath());
        System.out.println("Created Size: " + created.size());
        assertEquals(1, created.size());
        Document newDoc = created.get(0);

        // TODO: Figure out how to
//        System.out.println(newDoc.toJson());
//        System.out.println("***********************************************************");

//        System.out.println(newDoc.get(DarConstants.ID).toString());
//        System.out.println("***********************************************************");

//        System.out.println(newDoc.get(DarConstants.ID, ObjectId.class).toString());
//        System.out.println("***********************************************************");

//        Object id = newDoc.get(DarConstants.ID);
//        ObjectId objectId = newDoc.getObjectId(DarConstants.ID);
//        ObjectId objectId = new ObjectId(id.toString());

//        System.out.println(objectId);
//        System.out.println("***********************************************************");

        // TODO: This is the test I want to pass:
//        Response response = getJson(client, darPath(newDoc.getString(DarConstants.ID)));
//        assertEquals(200, response.getStatus());

    }

    @Ignore
    @Test
    public void testDarNotFound() throws Exception {
        Client client = ClientBuilder.newClient();
        Response response = getJson(client, darPath("invalid-5998-id"));
        assertEquals(404, response.getStatus());
    }

    private List<Document> retrieveDars(Client client, String url) throws IOException {
        return getJson(client, url).readEntity(new GenericType<List<Document>>() {});
    }

}
