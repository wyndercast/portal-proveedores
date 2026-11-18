package com.autoeastern.backend.shared.integration.valuekeep.auth;

import com.autoeastern.backend.shared.integration.valuekeep.config.ValuekeepProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class ValuekeepTokenServiceTest {

    @Test
    void cachesTokenUntilExpiration() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        server.expect(requestTo("https://identity.test/connect/token"))
                .andExpect(method(POST))
                .andRespond(withSuccess(
                        "{\"access_token\":\"token-1\",\"token_type\":\"Bearer\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));

        ValuekeepProperties properties = new ValuekeepProperties();
        properties.setTokenUrl("https://identity.test/connect/token");
        properties.setClientId("client");
        properties.setClientSecret("secret");

        ValuekeepTokenService service = new ValuekeepTokenService(client, properties);

        assertEquals("token-1", service.getAccessToken());
        assertEquals("token-1", service.getAccessToken());
        server.verify();
    }
}
