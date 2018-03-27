/*
 * Copyright (c) 2011-2017 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.micrometer.impl;

import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.Verticle;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.metrics.impl.DummyVertxMetrics;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.DatagramSocketMetrics;
import io.vertx.core.spi.metrics.EventBusMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.PoolMetrics;
import io.vertx.core.spi.metrics.TCPMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.backends.BackendRegistries;
import io.vertx.micrometer.backends.BackendRegistry;

import static io.vertx.micrometer.MetricsDomain.*;

/**
 * Metrics SPI implementation for Micrometer.
 *
 * @author Joel Takvorian
 */
public class VertxMetricsImpl extends AbstractMetrics implements VertxMetrics {
  private final BackendRegistry backendRegistry;
  private final String registryName;
  private final EventBusMetrics eventBusMetrics;
  private final DatagramSocketMetrics datagramSocketMetrics;
  private final VertxNetClientMetrics netClientMetrics;
  private final VertxNetServerMetrics netServerMetrics;
  private final VertxHttpClientMetrics httpClientMetrics;
  private final VertxHttpServerMetrics httpServerMetrics;
  private final VertxPoolMetrics poolMetrics;
  private final VertxVerticleMetrics verticleMetrics;

  /**
   * @param options Vertx Prometheus options
   */
  public VertxMetricsImpl(MicrometerMetricsOptions options, BackendRegistry backendRegistry) {
    super(backendRegistry.getMeterRegistry(), null);
    this.backendRegistry = backendRegistry;
    registryName = options.getRegistryName();
    MeterRegistry registry = backendRegistry.getMeterRegistry();

    eventBusMetrics = options.isMetricsCategoryDisabled(EVENT_BUS) ? null
      : new VertxEventBusMetrics(registry);
    datagramSocketMetrics = options.isMetricsCategoryDisabled(DATAGRAM_SOCKET) ? null
      : new VertxDatagramSocketMetrics(registry);
    netClientMetrics = options.isMetricsCategoryDisabled(NET_CLIENT) ? null
      : new VertxNetClientMetrics(registry);
    netServerMetrics = options.isMetricsCategoryDisabled(NET_SERVER) ? null
      : new VertxNetServerMetrics(registry);
    httpClientMetrics = options.isMetricsCategoryDisabled(HTTP_CLIENT) ? null
      : new VertxHttpClientMetrics(registry);
    httpServerMetrics = options.isMetricsCategoryDisabled(HTTP_SERVER) ? null
      : new VertxHttpServerMetrics(registry);
    poolMetrics = options.isMetricsCategoryDisabled(NAMED_POOLS) ? null
      : new VertxPoolMetrics(registry);
    verticleMetrics = options.isMetricsCategoryDisabled(VERTICLES) ? null
      : new VertxVerticleMetrics(registry);
  }

  @Override
  public void eventBusInitialized(EventBus bus) {
    backendRegistry.eventBusInitialized(bus);
  }

  @Override
  public void verticleDeployed(Verticle verticle) {
    if (verticleMetrics != null) {
      verticleMetrics.verticleDeployed(verticle);
    }
  }

  @Override
  public void verticleUndeployed(Verticle verticle) {
    if (verticleMetrics != null) {
      verticleMetrics.verticleUndeployed(verticle);
    }
  }

  @Override
  public EventBusMetrics createMetrics(EventBus eventBus) {
    if (eventBusMetrics != null) {
      return eventBusMetrics;
    }
    return DummyVertxMetrics.DummyEventBusMetrics.INSTANCE;
  }

  @Override
  public HttpServerMetrics<?, ?, ?> createMetrics(HttpServer httpServer, SocketAddress socketAddress, HttpServerOptions httpServerOptions) {
    if (httpServerMetrics != null) {
      return httpServerMetrics.forAddress(socketAddress);
    }
    return DummyVertxMetrics.DummyHttpServerMetrics.INSTANCE;
  }

  @Override
  public HttpClientMetrics<?, ?, ?, ?, ?> createMetrics(HttpClient httpClient, HttpClientOptions httpClientOptions) {
    if (httpClientMetrics != null) {
      return httpClientMetrics.forAddress(httpClientOptions.getLocalAddress());
    }
    return DummyVertxMetrics.DummyHttpClientMetrics.INSTANCE;
  }

  @Override
  public TCPMetrics<?> createMetrics(SocketAddress socketAddress, NetServerOptions netServerOptions) {
    if (netServerMetrics != null) {
      return netServerMetrics.forAddress(socketAddress);
    }
    return DummyVertxMetrics.DummyTCPMetrics.INSTANCE;
  }

  @Override
  public TCPMetrics<?> createMetrics(NetClientOptions netClientOptions) {
    if (netClientMetrics != null) {
      return netClientMetrics.forAddress(netClientOptions.getLocalAddress());
    }
    return DummyVertxMetrics.DummyTCPMetrics.INSTANCE;
  }

  @Override
  public DatagramSocketMetrics createMetrics(DatagramSocket datagramSocket, DatagramSocketOptions datagramSocketOptions) {
    if (datagramSocketMetrics != null) {
      return datagramSocketMetrics;
    }
    return DummyVertxMetrics.DummyDatagramMetrics.INSTANCE;
  }

  @Override
  public <P> PoolMetrics<?> createMetrics(P pool, String poolType, String poolName, int maxPoolSize) {
    if (poolMetrics != null) {
      return poolMetrics.forInstance(poolType, poolName, maxPoolSize);
    }
    return DummyVertxMetrics.DummyWorkerPoolMetrics.INSTANCE;
  }

  @Override
  public boolean isMetricsEnabled() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public void close() {
    BackendRegistries.stop(registryName);
  }
}
