package io.vertx.micrometer;

import io.micrometer.core.instrument.config.MeterFilter;
import io.vertx.core.*;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.micrometer.backends.BackendRegistries;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;

import static io.vertx.micrometer.RegistryInspector.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Joel Takvorian
 */
@RunWith(VertxUnitRunner.class)
public class VertxHttpClientServerMetricsTest {

  private static final int HTTP_SENT_COUNT = 68;
  private static final int SENT_COUNT = HTTP_SENT_COUNT + 1;
  private static final String SERVER_RESPONSE = "some text";
  private static final String CLIENT_REQUEST = "pitchounette";
  private static final long REQ_DELAY = 30L;

  private final int concurrentClients = ForkJoinPool.commonPool().getParallelism();
  private final String registryName = UUID.randomUUID().toString();
  private HttpServer httpServer;
  private Vertx vertx;

  @Before
  public void setUp(TestContext ctx) {
    vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(new MicrometerMetricsOptions()
        .setPrometheusOptions(new VertxPrometheusOptions().setEnabled(true))
      .setRegistryName(registryName)
      .addLabels(Label.REMOTE, Label.LOCAL, Label.HTTP_PATH, Label.EB_ADDRESS)
      .setEnabled(true)))
      .exceptionHandler(ctx.exceptionHandler());

    // Filter out remote labels
    BackendRegistries.getNow(registryName).config().meterFilter(
      MeterFilter.replaceTagValues(Label.REMOTE.toString(), s -> "_", "127.0.0.1:9195"));

    // Setup server
    Async serverReady = ctx.async();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start(Promise<Void> future) throws Exception {
        httpServer = vertx.createHttpServer();
        httpServer
          .webSocketHandler(ws ->
            ws.handler(event -> {
              vertx.setTimer(REQ_DELAY, timer -> {
                ws.writeTextMessage(SERVER_RESPONSE);
                ws.end();
              });
            }))
          .requestHandler(req -> {
            // Timer as artificial processing time
            vertx.setTimer(REQ_DELAY, handler ->
              req.routed("MyRoute").response().setChunked(true).putHeader("Content-Type", "text/plain").end(SERVER_RESPONSE));
          })
          .listen(9195, "127.0.0.1", r -> {
            if (r.failed()) {
              ctx.fail(r.cause());
            } else {
              serverReady.complete();
            }
          });
      }
    });
    serverReady.awaitSuccess();
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void shouldReportHttpClientMetrics(TestContext ctx) {
    runClientRequests(ctx, false);

    waitForValue(vertx, ctx, registryName, "vertx.http.client.bytesReceived[local=?,remote=127.0.0.1:9195]$COUNT",
      value -> value.intValue() == concurrentClients * HTTP_SENT_COUNT);

    List<RegistryInspector.Datapoint> datapoints = listDatapoints(registryName, startsWith("vertx.http.client."));
    assertThat(datapoints).hasSize(15).contains(
        dp("vertx.http.client.queue.size[local=?,remote=127.0.0.1:9195]$VALUE", 0),
        dp("vertx.http.client.queue.delay[local=?,remote=127.0.0.1:9195]$COUNT", concurrentClients * HTTP_SENT_COUNT),
        dp("vertx.http.client.bytesReceived[local=?,remote=127.0.0.1:9195]$COUNT", concurrentClients * HTTP_SENT_COUNT),
        dp("vertx.http.client.bytesReceived[local=?,remote=127.0.0.1:9195]$TOTAL", concurrentClients * HTTP_SENT_COUNT * SERVER_RESPONSE.getBytes().length),
        dp("vertx.http.client.bytesSent[local=?,remote=127.0.0.1:9195]$COUNT", concurrentClients * HTTP_SENT_COUNT),
        dp("vertx.http.client.bytesSent[local=?,remote=127.0.0.1:9195]$TOTAL", concurrentClients * HTTP_SENT_COUNT * CLIENT_REQUEST.getBytes().length),
        dp("vertx.http.client.requestCount[local=?,method=POST,path=/resource,remote=127.0.0.1:9195]$COUNT", concurrentClients * HTTP_SENT_COUNT),
        dp("vertx.http.client.responseCount[code=200,local=?,method=POST,path=/resource,remote=127.0.0.1:9195]$COUNT", concurrentClients * HTTP_SENT_COUNT));

    assertThat(datapoints).extracting(Datapoint::id).contains(
      "vertx.http.client.responseTime[code=200,local=?,method=POST,path=/resource,remote=127.0.0.1:9195]$TOTAL_TIME",
      "vertx.http.client.responseTime[code=200,local=?,method=POST,path=/resource,remote=127.0.0.1:9195]$COUNT",
      "vertx.http.client.responseTime[code=200,local=?,method=POST,path=/resource,remote=127.0.0.1:9195]$MAX",
      "vertx.http.client.requests[local=?,method=POST,path=/resource,remote=127.0.0.1:9195]$VALUE",
      "vertx.http.client.connections[local=?,remote=127.0.0.1:9195]$VALUE");
  }

  @Test
  public void shouldReportHttpServerMetricsWithoutWS(TestContext ctx) {
    runClientRequests(ctx, false);

    waitForValue(vertx, ctx, registryName, "vertx.http.server.bytesReceived[local=127.0.0.1:9195,remote=_]$COUNT",
      value -> value.intValue() == concurrentClients * HTTP_SENT_COUNT);

    List<RegistryInspector.Datapoint> datapoints = listDatapoints(registryName, startsWith("vertx.http.server."));
    assertThat(datapoints).extracting(Datapoint::id).containsOnly(
      "vertx.http.server.requestCount[code=200,local=127.0.0.1:9195,method=POST,path=/resource,remote=_,route=MyRoute]$COUNT",
      "vertx.http.server.requests[local=127.0.0.1:9195,method=POST,path=/resource,remote=_]$VALUE",
      "vertx.http.server.connections[local=127.0.0.1:9195,remote=_]$VALUE",
      "vertx.http.server.bytesReceived[local=127.0.0.1:9195,remote=_]$COUNT",
      "vertx.http.server.bytesReceived[local=127.0.0.1:9195,remote=_]$TOTAL",
      "vertx.http.server.bytesSent[local=127.0.0.1:9195,remote=_]$COUNT",
      "vertx.http.server.bytesSent[local=127.0.0.1:9195,remote=_]$TOTAL",
      "vertx.http.server.responseTime[code=200,local=127.0.0.1:9195,method=POST,path=/resource,remote=_,route=MyRoute]$TOTAL_TIME",
      "vertx.http.server.responseTime[code=200,local=127.0.0.1:9195,method=POST,path=/resource,remote=_,route=MyRoute]$COUNT",
      "vertx.http.server.responseTime[code=200,local=127.0.0.1:9195,method=POST,path=/resource,remote=_,route=MyRoute]$MAX");

    assertThat(datapoints).contains(
      dp("vertx.http.server.bytesReceived[local=127.0.0.1:9195,remote=_]$COUNT", concurrentClients * HTTP_SENT_COUNT),
      dp("vertx.http.server.bytesReceived[local=127.0.0.1:9195,remote=_]$TOTAL", concurrentClients * HTTP_SENT_COUNT * CLIENT_REQUEST.getBytes().length),
      dp("vertx.http.server.bytesSent[local=127.0.0.1:9195,remote=_]$COUNT", concurrentClients * HTTP_SENT_COUNT),
      dp("vertx.http.server.bytesSent[local=127.0.0.1:9195,remote=_]$TOTAL", concurrentClients * HTTP_SENT_COUNT * SERVER_RESPONSE.getBytes().length),
      dp("vertx.http.server.requestCount[code=200,local=127.0.0.1:9195,method=POST,path=/resource,remote=_,route=MyRoute]$COUNT", concurrentClients * HTTP_SENT_COUNT));
  }

  @Test
  public void shouldReportHttpServerMetrics(TestContext ctx) {
    runClientRequests(ctx, true);

    // Remark, with websockets, two extra requests are performed so increase the expected value
    waitForValue(vertx, ctx, registryName, "vertx.http.server.bytesReceived[local=127.0.0.1:9195,remote=_]$COUNT",
      value -> value.intValue() == concurrentClients * (SENT_COUNT + 2));

    List<RegistryInspector.Datapoint> datapoints = listDatapoints(registryName, startsWith("vertx.http.server."));
    assertThat(datapoints).extracting(Datapoint::id).containsOnly(
      "vertx.http.server.requestCount[code=200,local=127.0.0.1:9195,method=POST,path=/resource,remote=_,route=MyRoute]$COUNT",
      "vertx.http.server.requests[local=127.0.0.1:9195,method=POST,path=/resource,remote=_]$VALUE",
      "vertx.http.server.connections[local=127.0.0.1:9195,remote=_]$VALUE",
      "vertx.http.server.wsConnections[local=127.0.0.1:9195,remote=_]$VALUE",
      "vertx.http.server.bytesReceived[local=127.0.0.1:9195,remote=_]$COUNT",
      "vertx.http.server.bytesReceived[local=127.0.0.1:9195,remote=_]$TOTAL",
      "vertx.http.server.bytesSent[local=127.0.0.1:9195,remote=_]$COUNT",
      "vertx.http.server.bytesSent[local=127.0.0.1:9195,remote=_]$TOTAL",
      "vertx.http.server.responseTime[code=200,local=127.0.0.1:9195,method=POST,path=/resource,remote=_,route=MyRoute]$TOTAL_TIME",
      "vertx.http.server.responseTime[code=200,local=127.0.0.1:9195,method=POST,path=/resource,remote=_,route=MyRoute]$COUNT",
      "vertx.http.server.responseTime[code=200,local=127.0.0.1:9195,method=POST,path=/resource,remote=_,route=MyRoute]$MAX",
      // Following ones result from the WS connection
      "vertx.http.server.requests[local=127.0.0.1:9195,method=GET,path=/,remote=_]$VALUE",
      "vertx.http.server.requestCount[code=200,local=127.0.0.1:9195,method=GET,path=/,remote=_,route=]$COUNT",
      "vertx.http.server.responseTime[code=200,local=127.0.0.1:9195,method=GET,path=/,remote=_,route=]$TOTAL_TIME",
      "vertx.http.server.responseTime[code=200,local=127.0.0.1:9195,method=GET,path=/,remote=_,route=]$COUNT",
      "vertx.http.server.responseTime[code=200,local=127.0.0.1:9195,method=GET,path=/,remote=_,route=]$MAX");

    assertThat(datapoints).contains(
      dp("vertx.http.server.bytesReceived[local=127.0.0.1:9195,remote=_]$COUNT", concurrentClients * (SENT_COUNT + 2)),
      dp("vertx.http.server.bytesSent[local=127.0.0.1:9195,remote=_]$COUNT", concurrentClients * (SENT_COUNT + 1)),
      dp("vertx.http.server.requestCount[code=200,local=127.0.0.1:9195,method=POST,path=/resource,remote=_,route=MyRoute]$COUNT", concurrentClients * HTTP_SENT_COUNT));
  }

  @Test
  public void shouldIgnoreInternalEventbusMetrics(TestContext ctx) {
    runClientRequests(ctx, true);

    waitForValue(vertx, ctx, registryName, "vertx.http.server.bytesReceived[local=127.0.0.1:9195,remote=_]$COUNT",
      value -> value.intValue() == concurrentClients * (SENT_COUNT + 2));

    List<RegistryInspector.Datapoint> datapoints = listDatapoints(registryName, startsWith("vertx.eventbus."));
    assertThat(datapoints).isEmpty();
  }

  private void runClientRequests(TestContext ctx, boolean ws) {
    Async clientsFinished = ctx.async(concurrentClients);
    for (int i = 0; i < concurrentClients; i++) {
      ForkJoinPool.commonPool().execute(() -> {
        HttpClient httpClient = vertx.createHttpClient();
        httpRequest(httpClient, ctx);
        if (ws) {
          wsRequest(httpClient, ctx);
        }
        clientsFinished.countDown();
      });
    }
    clientsFinished.awaitSuccess();
  }

  private void httpRequest(HttpClient httpClient, TestContext ctx) {
    Async async = ctx.async(HTTP_SENT_COUNT);
    for (int i = 0; i < HTTP_SENT_COUNT; i++) {
      httpClient.request(HttpMethod.POST, 9195, "127.0.0.1", "/resource")
        .compose(req -> req
          .send(CLIENT_REQUEST)
          .compose(response -> {
            if (response.statusCode() != 200) {
              return Future.failedFuture(response.statusMessage());
            } else {
              return response.body();
            }
          }))
        .onComplete(ctx.asyncAssertSuccess(v -> async.countDown()));
    }
    async.await();
  }

  private void wsRequest(HttpClient httpClient, TestContext ctx) {
    Async async = ctx.async();
    httpClient.webSocket(9195, "127.0.0.1", "", ctx.asyncAssertSuccess(ws -> {
      ws.handler(event -> {
        async.complete();
        ws.close();
      });
      ws.writeTextMessage(CLIENT_REQUEST);
    }));
    async.await();
  }
}
