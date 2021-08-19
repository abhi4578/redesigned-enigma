package io.iudx.calculator.deploy;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import java.util.EnumSet;


import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.zookeeper.ZookeeperClusterManager;

import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.Option;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.json.JsonObject;
import io.vertx.core.DeploymentOptions;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.nio.file.Files;

import io.vertx.core.metrics.MetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.Label;
import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.micrometer.backends.BackendRegistries;
// JVM metrics imports

import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

public class Deployer {
  private static final Logger LOGGER = LogManager.getLogger(Deployer.class);
  private static ClusterManager mgr;
  private static Vertx vertx;

  public static void recursiveDeploy(Vertx vertx, JsonObject configs, int i) {
    if (i >= configs.getJsonArray("modules").size()) {
      LOGGER.info("Deployed all");
      return;
    }
    JsonObject config = configs.getJsonArray("modules").getJsonObject(i);
    String moduleName = config.getString("id");
    int numInstances = config.getInteger("verticleInstances");
    vertx.deployVerticle(moduleName,
                           new DeploymentOptions()
                                  .setInstances(numInstances)
                                  .setConfig(config),
                          ar -> {
      if (ar.succeeded()) {
        LOGGER.info("Deployed " + moduleName);
        recursiveDeploy(vertx, configs, i+1);
      } else {
        LOGGER.fatal("Failed to deploy " + moduleName + " cause:", ar.cause());
      }
    });
  }
	public static ClusterManager getClusterManager(List<String> zookeepers, String clusterId) {
      JsonObject zkConfig = new JsonObject();
      zkConfig.put("zookeeperHosts", String.join(",", zookeepers));
      zkConfig.put("rootPath", clusterId);
      return new ZookeeperClusterManager(zkConfig);
	}

  public static MetricsOptions getMetricsOptions() {
    return new MicrometerMetricsOptions()
        .setPrometheusOptions(
            new VertxPrometheusOptions().setEnabled(true).setStartEmbeddedServer(true)
                .setEmbeddedServerOptions(new HttpServerOptions().setPort(9000)))
        // .setPublishQuantiles(true))
        .setLabels(EnumSet.of(Label.EB_ADDRESS, Label.EB_FAILURE, Label.HTTP_CODE,
            Label.HTTP_METHOD))
        .setEnabled(true);
  }

  public static void setJVMmetrics() {
    MeterRegistry registry = BackendRegistries.getDefaultNow();
    new ClassLoaderMetrics().bindTo(registry);
    new JvmMemoryMetrics().bindTo(registry);
    new JvmGcMetrics().bindTo(registry);
    new ProcessorMetrics().bindTo(registry);
    new JvmThreadMetrics().bindTo(registry);

  }

  public static void deploy(String configPath, String host) {
    String config;
    try {
     config = new String(Files.readAllBytes(Paths.get(configPath)), StandardCharsets.UTF_8);
    } catch (Exception e) {
      LOGGER.fatal("Couldn't read configuration file");
      return;
    }
    if (config.length() < 1) {
      LOGGER.fatal("Couldn't read configuration file");
      return;
    }
    JsonObject configuration = new JsonObject(config);
    List<String> zookeepers = configuration.getJsonArray("zookeepers").getList();
    String clusterId = configuration.getString("clusterId");
    mgr = getClusterManager(zookeepers, clusterId);
    EventBusOptions ebOptions = new EventBusOptions().setClustered(true).setHost(host);
    VertxOptions options = new VertxOptions().setClusterManager(mgr).setEventBusOptions(ebOptions)
        .setMetricsOptions(getMetricsOptions());

    Vertx.clusteredVertx(options, res -> {
      if (res.succeeded()) {
        vertx = res.result();
        setJVMmetrics();
        recursiveDeploy(vertx, configuration, 0);
      } else {
        LOGGER.fatal("Could not join cluster");
      }
    });

  }

  public static void gracefulShutdown() {
    Set<String> deployIDSet = vertx.deploymentIDs();
    Logger LOGGER = LogManager.getLogger(Deployer.class);
    LOGGER.info("Shutting down the application");
    CountDownLatch latch_verticles = new CountDownLatch(deployIDSet.size());
    CountDownLatch latch_cluster = new CountDownLatch(1);
    CountDownLatch latch_vertx = new CountDownLatch(1);
    LOGGER.debug("number of verticles being undeployed are:" + deployIDSet.size());
    for (String deploymentID : deployIDSet) {
      vertx.undeploy(deploymentID, handler -> {
        if (handler.succeeded()) {
          LOGGER.debug(deploymentID + " verticle  successfully Undeployed");
          latch_verticles.countDown();
        } else {
          LOGGER.warn(deploymentID + "Undeploy failed!");
        }

      });
    }

    try {
      latch_verticles.await(5, TimeUnit.SECONDS);
      LOGGER.info("All the verticles undeployed");
      mgr.leave(handler -> {
        if (handler.succeeded()) {
          LOGGER.info("Zookeeper client succesfully left:");
          latch_cluster.countDown();

        } else {
          LOGGER.warn("Error while Zookeeper client leaving, reason:" + handler.cause());
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
    }

    try {
      latch_cluster.await(5, TimeUnit.SECONDS);
      vertx.close(handler -> {
        if (handler.succeeded()) {
          LOGGER.info("vertx closed succesfully");
          latch_vertx.countDown();
        } else {
          LOGGER.warn("Vertx didn't close properly, reason:" + handler.cause());
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
    }

    try {
      latch_vertx.await(5, TimeUnit.SECONDS);
      // then shut down log4j
      if( LogManager.getContext() instanceof LoggerContext ) {
        LOGGER.debug("Shutting down log4j2");
        LogManager.shutdown((LoggerContext) LogManager.getContext());
      } else
        LOGGER.warn("Unable to shutdown log4j2");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) {
    CLI cli = CLI.create("IUDX calculator").setSummary("A CLI to deploy the calculator")
        .addOption(new Option().setLongName("help").setShortName("h").setFlag(true)
            .setDescription("display help"))
        .addOption(new Option().setLongName("config").setShortName("c")
            .setRequired(true).setDescription("configuration file"))
        .addOption(new Option().setLongName("host").setShortName("i").setRequired(true)
            .setDescription("public host"));;

    StringBuilder usageString = new StringBuilder();
    cli.usage(usageString);
    CommandLine commandLine = cli.parse(Arrays.asList(args), false);
    if (commandLine.isValid() && !commandLine.isFlagEnabled("help")) {
      String configPath = commandLine.getOptionValue("config");
      String host = commandLine.getOptionValue("host");
      deploy(configPath,host);
      Runtime.getRuntime().addShutdownHook(new Thread(() -> gracefulShutdown()));
    } else {
      LOGGER.info(usageString);
    }
  }

}
