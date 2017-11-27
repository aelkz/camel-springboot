package my.company;

import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.ws.rs.core.MediaType;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangeException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.bean.validator.BeanValidationException;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;

import my.company.model.ApiResponse;
import my.company.model.CountryPojo;
import my.company.model.HeadersPojo;
import my.company.model.UserPojo;

@Component("mybuilder")
public class MyBuilder extends RouteBuilder {
	private final static Logger log = LoggerFactory.getLogger(MyBuilder.class);
	
	private final static UserPojo DUMMY_USER = new UserPojo("JohnDoe", 21);
	private final static ApiResponse SUCC = new ApiResponse(0,"OK");
	
	public final static String HEADER_BUSINESSID = "businessId";
	
	@Override
	public void configure() throws Exception {
		/************************
		 * common exception handlers for all routes defined in this RouteBuilder
		 ************************/
		onException(JsonProcessingException.class)
			.handled(true)
			.to("log:"+MyBuilder.class.getName()+"?showAll=true&multiline=true&level=ERROR")
			.removeHeaders("*",HEADER_BUSINESSID) //don't let message headers get inserted in the http response
			.setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
			.bean("mybuilder","errorResponse(4000,'Invalid json content')");
			
		onException(Exception.class)
			.handled(true)
			.to("log:"+MyBuilder.class.getName()+"?showAll=true&multiline=true&level=ERROR")
			.removeHeaders("*",HEADER_BUSINESSID) //don't let message headers get inserted in the http response
			.setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
			.bean("mybuilder","errorResponse(*)");
		
		/************************
		 * Rest configuration. There should be only one in a CamelContext
		 ************************/
		restConfiguration().component("servlet") //Requires "CamelServlet" to be registered
			.bindingMode(RestBindingMode.json)
			//Customize in/out Jackson objectmapper, see JsonDataFormat. Two different instances): json.in.*, json.out.*
			.dataFormatProperty("json.in.moduleClassNames", "com.fasterxml.jackson.datatype.jsr310.JavaTimeModule")
			.dataFormatProperty("json.out.include", "NON_NULL")
			.dataFormatProperty("json.out.disableFeatures", "WRITE_DATES_AS_TIMESTAMPS")
			.dataFormatProperty("json.out.moduleClassNames", "com.fasterxml.jackson.datatype.jsr310.JavaTimeModule")
			
			
			//Enable swagger endpoint. It's actually served by a Camel route
			.apiContextPath("/swagger") //swagger endpoint path; Final URL: Camel path + apiContextPath: /api/swagger
			.apiContextRouteId("swagger") //id of route providing the swagger endpoint
			
			.contextPath("/api") //base.path swagger property; use the mapping URL set for CamelServlet camel.component.servlet.mapping.contextPath
			.apiProperty("api.title", "Example REST api")
			.apiProperty("api.version", "1.0")
			.apiProperty("schemes", "" ) //Setting empty string as scheme to support relative urls
			.apiProperty("host", "") //Setting empty string as host so swagger-ui make relative url calls. By default 0.0.0.0 is used
			;
		/************************
		 * Rest endpoints. Multiple can be defined (in multiple RouteBuilder), but should map different URL path
		 ************************/
		rest("/user").description("User API")
			.produces(MediaType.APPLICATION_JSON).consumes(MediaType.APPLICATION_JSON)
			.skipBindingOnErrorCode(false) //Enable json marshalling for body in case of errors
		.get("/{id}")
			//swagger
			.description("Query user")
			.param().name("id").type(RestParamType.path).description("Id of the user. Must be number and less than 100.").required(true).dataType("string").endParam()
			.param().name(HEADER_BUSINESSID).type(RestParamType.header).description("Business transactionid. Defaults to a random uuid").dataType("string").endParam()
			.responseMessage().code(200).responseModel(UserPojo.class).endResponseMessage() //OK
			.responseMessage().code(500).responseModel(ApiResponse.class).endResponseMessage() //Not-OK
			//route
			.route().routeId("user-get")
				.log("Get user: ${header.id}")
				.setBody().simple("${headers}",HeadersPojo.class)
				.to("bean-validator:validateHeaders") //or .validate().simple("${header.id} < 100")
				.setBody(constant(DUMMY_USER))
				.removeHeaders("*","businessId")
			.endRest()
		.post("/").type(UserPojo.class)
			//swagger
			.description("Send user")
			.param().name(HEADER_BUSINESSID).type(RestParamType.header).description("Business transactionid. Defaults to a random uuid").dataType("string").endParam()
			.responseMessage().code(200).responseModel(ApiResponse.class).endResponseMessage() //OK
			.responseMessage().code(400).responseModel(ApiResponse.class).message("Unexpected body").endResponseMessage() //Wrong input
			.responseMessage().code(500).responseModel(ApiResponse.class).endResponseMessage() //Not-OK
			//route 
			.route().routeId("post-user")
				.log("User received: ${body}").id("received-user") //This step gets an id, so we can refer it in test
				.setBody(constant(SUCC))
				.removeHeaders("*",HEADER_BUSINESSID)
			.endRest();
		
		//Another rest dsl
		rest("/country").description("Country API")
			.skipBindingOnErrorCode(false)
		.post("/").type(CountryPojo.class)
			//swagger
			.description("Send country")
			.responseMessage().code(200).endResponseMessage()
			.route().routeId("post-country")
				.log("Country received: ${body}").id("received-country") //This step gets an id, so we can refer it in test
				.setBody(constant(null))//Don't return anything in the body, so no responseModel() or outType() is required
			.endRest()
		;
		
		rest("/secure").description("Basic auth. Try name:'user' passwd:'secret'.")
		.get().outType(ApiResponse.class)
			.route().routeId("secure-get")
			.log("Secure is called")
			.setBody(constant(SUCC))
			.removeHeaders("*",HEADER_BUSINESSID)
		.endRest();
		
	}
	
	//Helper methods used in these routes. It's a good idea to keep them in the RouteBuilder for readability if they are simple.
	//In a real world scenario the response is probably more complicated based on the current exchange
	public static ApiResponse errorResponse(int code, String message){
		return new ApiResponse(code, message);
	}
	
	public static ApiResponse errorResponse(@ExchangeException Exception ex){
		String message;
		if (ex instanceof BeanValidationException){
			message = Optional.ofNullable(((BeanValidationException)ex).getConstraintViolations()).orElseGet(Collections::emptySet)
					.stream()
					.map((v)->"'"+v.getPropertyPath()+"' "+v.getMessage())
					.collect(Collectors.joining("; "));
		} else {
			message = ex.getMessage();
		}
		return new ApiResponse(5000, ex.getMessage());
	}
	
}
