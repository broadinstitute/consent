package org.broadinstitute.consent.http.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.auth.AuthenticationException;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.broadinstitute.consent.http.configurations.GoogleOAuth2Config;
import org.broadinstitute.consent.http.models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Optional;


public class OAuthAuthenticator extends AbstractOAuthAuthenticator  {

    private GoogleOAuth2Config config;
    private final String tokenInfoUrl = "https://www.googleapis.com/oauth2/v3/tokeninfo?access_token=";
    private static final Logger logger = LoggerFactory.getLogger(OAuthAuthenticator.class);
    private static HttpClient httpClient;


    public static void initInstance(GoogleOAuth2Config config) {
        AuthenticatorAPIHolder.setInstance(new OAuthAuthenticator(config));
    }

    private OAuthAuthenticator(GoogleOAuth2Config config) {
        this.config = config;
        this.httpClient = HttpClients.createDefault();
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public Optional<User> authenticate(String bearer) {
        try {
            HashMap<String, Object> tokenInfo = validateToken(bearer);
            String email = tokenInfo.get("email").toString();
            User user = new User(email);
            return Optional.of(user);
        } catch (AuthenticationException e) {
            logger.error("Error authenticating credentials.");
            return Optional.empty();
        }

    }

    private HashMap<String, Object> validateToken(String accessToken) throws AuthenticationException {
        HashMap<String, Object> tokenInfo = null;
        HttpPost httppost = new HttpPost(tokenInfoUrl + accessToken);
        try {
            HttpResponse response = httpClient.execute(httppost);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                InputStream tokenInfoStream = entity.getContent();
                String result = IOUtils.toString(tokenInfoStream);
                tokenInfo = new ObjectMapper().readValue(result, HashMap.class);
                tokenInfoStream.close();
            }
        } catch (IOException e) {
            unauthorized(accessToken);
        }
        return tokenInfo;
    }


    private void unauthorized(String accessToken) throws AuthenticationException {
        throw new AuthenticationException("Provided access token or user credential is either null or empty or does not have permissions to access this resource." + accessToken);
    }


}