package my.company;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.test.spring.CamelSpringBootRunner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import static org.hamcrest.Matchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import my.company.model.ApiResponse;

@RunWith(CamelSpringBootRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext
public class BusinessIdTest extends Assert {
	private static final Logger log = LoggerFactory.getLogger(BusinessIdTest.class);

	//@Autowired - works only in Camel 2.19.3+
	protected FluentProducerTemplate fluentProducerTemplate;

	@Autowired
	CamelContext context;
	
	@Autowired
	ObjectMapper objectmapper;

	@Before
	public void before() throws Exception {
		fluentProducerTemplate = context.createFluentProducerTemplate();
	}

	@Test
	public void noBusinessId() throws Exception {
		// Call get
		Exchange response = fluentProducerTemplate.to("undertow:http://localhost:{{local.server.port}}/api/user/1")
				.withHeader(Exchange.HTTP_METHOD, HttpMethod.GET)
				.withHeader(Exchange.ACCEPT_CONTENT_TYPE, MediaType.APPLICATION_JSON)
				.send();
		
		assertEquals(200, response.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE));
		assertTrue(response.getIn().getHeader(MyBuilder.HEADER_BUSINESSID,String.class).length() == 36);
	}

	@Test
	public void shortBusinessId() throws Exception {
		// Call get
		Exchange response = fluentProducerTemplate.to("undertow:http://localhost:{{local.server.port}}/api/user/1")
				.withHeader(Exchange.HTTP_METHOD, HttpMethod.GET)
				.withHeader(Exchange.ACCEPT_CONTENT_TYPE, MediaType.APPLICATION_JSON)
				.withHeader(MyBuilder.HEADER_BUSINESSID,"X")
				.send();
		
		assertEquals(500, response.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE));
		
		String businessId = response.getIn().getHeader(MyBuilder.HEADER_BUSINESSID,String.class);
		log.info("response: {}",response.getIn().getBody(String.class));
		ApiResponse apiResponse = objectmapper.readValue(response.getIn().getBody(String.class), ApiResponse.class);
		assertThat(apiResponse.getMessage(), containsString("property: businessId; value: X; constraint: length must be between 16 and 48;"));
		
	}

}
