package io.iudx.calculator.apiserver;
import io.iudx.calculator.adder.AdderService;
import io.iudx.calculator.divider.DividerService;
import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.Router;
import io.vertx.core.http.HttpServerResponse;
import java.io.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// import io.prometheus.client.

public class APIServerVerticle extends AbstractVerticle {
	static String historyFileName = "/data/api-server-history.txt";
	private static final Logger LOGGER = LogManager.getLogger(APIServerVerticle.class);
	@Override
	public void start() {
		// LOGGER.fatal("API server verticle deployed");
		LOGGER.info("Starting an API server");
		Router router = Router.router(vertx);
		
		Pattern addpattern = Pattern.compile("/add/.*/.*");
		Pattern divpattern = Pattern.compile("/divide/.*/.*");
		
		router.route("/add/:x/:y/").handler(routingContext -> {
			
			HttpServerResponse response = routingContext.response();
			AdderService adderService = AdderService.createProxy(vertx, "adder-service-address");
			Integer x = new Integer(routingContext.request().getParam("x"));
			Integer y = new Integer(routingContext.request().getParam("y"));

			adderService.operate(x, y, res -> {
				String reply = "";
				if (res.succeeded()) {
					reply = String.format("%d+ %d = %d\n", x, y, res.result());
					LOGGER.info("Successfully added");
			
				} else {
					reply = String.format("%d + %d = %s\n", x, y, "ERROR, " + res.cause());
			
					LOGGER.error("Error at adder service:"+ res.cause());
				}
				try {
					FileWriter fileWriter = new FileWriter(historyFileName, true);
					fileWriter.write(reply);
					fileWriter.close();
				} catch (IOException ex) {
					reply = "ERROR: " + ex.getMessage();
					LOGGER.error("Error writing to history file at adder service"+ex.toString());
					
				}
				
				response.end(reply);
			});
			
		
		});

		router.route("/divide/:x/:y/").handler(routingContext -> {
			
			HttpServerResponse response = routingContext.response();
			DividerService dividerService = DividerService.createProxy(vertx, "divider-service-address");
			Integer x = new Integer(routingContext.request().getParam("x"));
			Integer y = new Integer(routingContext.request().getParam("y"));

			dividerService.operate(x, y, res_div -> {
				String reply = "";
				if (res_div.succeeded()) {
					reply = String.format("%d / %d = %f\n", x, y, res_div.result());
					LOGGER.info("Successfully divided");
			
				} else {
					reply = String.format("%d / %d = %s\n", x, y, "ERROR, " + res_div.cause());
			
					LOGGER.error("Error at divide service:"+ res_div.cause());
				}
				try {
					FileWriter fileWriter = new FileWriter(historyFileName, true);
					fileWriter.write(reply);
					fileWriter.close();
				} catch (IOException ex) {
					reply = "ERROR: " + ex.getMessage();
					LOGGER.error("Error writing to history file at divide service"+ex.toString());
				}
				
				response.end(reply);
			});
			
		
		});
		
		router.route("/history").handler(routingContext -> {
			HttpServerResponse response = routingContext.response();
			response.sendFile(historyFileName);
			response.end();
		});

		router.route("/clear_history").handler(routingContext -> {
			HttpServerResponse response = routingContext.response();
			String reply = "Done";
			try {
				FileWriter fileWriter = new FileWriter(historyFileName);
				fileWriter.close();
				LOGGER.info("Succesfully written to the file " + historyFileName);
			} catch (IOException ex) {
				reply = "ERROR: " + ex.getMessage();
				LOGGER.error("Error writing to history file at /history"+ex.toString());
			}
			response.end(reply);
		});

		vertx.createHttpServer().requestHandler(router).listen(8080);
	}

	@Override
	public void stop() {
		System.out.println("Stopping an API server");
	}
}
