package com.autoeastern.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"valuekeep.base-url=https://valuekeep.test",
		"valuekeep.token-url=https://identity.test/connect/token",
		"valuekeep.client-id=test-client",
		"valuekeep.client-secret=test-secret",
		"valuekeep.tenant-key=test-tenant",
		"valuekeep.org-key=test-org",
		"valuekeep.company-key=test-company"
})
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
