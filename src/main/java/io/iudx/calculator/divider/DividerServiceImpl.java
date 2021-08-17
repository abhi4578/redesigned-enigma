package io.iudx.calculator.divider;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DividerServiceImpl implements DividerService {
	private static final Logger LOGGER = LogManager.getLogger(DividerServiceImpl.class);
	public DividerServiceImpl(Vertx vertx) {
	}

	@Override
	public void operate(Integer a, Integer b, Handler<AsyncResult<Float>> resultHandler) {
		LOGGER.debug("Called me");
		resultHandler.handle(Future.succeededFuture((float) a / (float) b));
	}
}