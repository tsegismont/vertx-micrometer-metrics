/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.vertx.micrometer.tests.impl.meters;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.vertx.micrometer.Match;
import io.vertx.micrometer.MatchType;
import io.vertx.micrometer.backends.BackendRegistries;
import io.vertx.micrometer.impl.meters.CustomGauge;
import io.vertx.micrometer.impl.meters.CustomGaugeBuilder;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.atomic.LongAdder;

import static io.vertx.micrometer.Label.EB_ADDRESS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Joel Takvorian
 */
public class GaugesTest {

  @Test
  public void shouldAliasGaugeLabel() {
    MeterRegistry registry = new SimpleMeterRegistry();
    BackendRegistries.registerMatchers(registry, Collections.singletonList(new Match()
      .setLabel("address")
      .setType(MatchType.REGEX)
      .setValue("addr1")
      .setAlias("1")));
    CustomGauge g1 = new CustomGaugeBuilder("my_gauge", LongAdder::doubleValue).tags(Tags.of(EB_ADDRESS.toString(), "addr1")).register(registry);
    g1.increment();
    g1.increment();
    CustomGauge g2 = new CustomGaugeBuilder("my_gauge", LongAdder::doubleValue).tags(Tags.of(EB_ADDRESS.toString(), "addr2")).register(registry);
    g2.increment();

    Meter meter = registry.get("my_gauge").tags("address", "1").meter();
    assertThat(meter.getId().getType()).isEqualTo(Type.GAUGE);
    Iterable<Measurement> measurements = meter.measure();
    assertThat(measurements).hasSize(1).first().satisfies(measurement -> {
      assertThat(measurement.getStatistic()).isEqualTo(Statistic.VALUE);
      assertThat(measurement.getValue()).isEqualTo(2d);
    });
    meter = registry.find("my_gauge").tags("address", "addr1").meter();
    assertThat(meter).isNull();
    meter = registry.get("my_gauge").tags("address", "addr2").meter();
    assertThat(meter.getId().getType()).isEqualTo(Type.GAUGE);
    measurements = meter.measure();
    assertThat(measurements).hasSize(1).first().satisfies(measurement -> {
      assertThat(measurement.getStatistic()).isEqualTo(Statistic.VALUE);
      assertThat(measurement.getValue()).isEqualTo(1d);
    });
  }

  @Test
  public void shouldIgnoreGaugeLabel() {
    MeterRegistry registry = new SimpleMeterRegistry();
    BackendRegistries.registerMatchers(registry, Collections.singletonList(new Match()
      .setLabel("address")
      .setType(MatchType.REGEX)
      .setValue(".*")
      .setAlias("_")));
    CustomGauge g1 = new CustomGaugeBuilder("my_gauge", LongAdder::doubleValue).tags(Tags.of(EB_ADDRESS.toString(), "addr1")).register(registry);
    g1.increment();
    g1.increment();
    CustomGauge g2 = new CustomGaugeBuilder("my_gauge", LongAdder::doubleValue).tags(Tags.of(EB_ADDRESS.toString(), "addr2")).register(registry);
    g2.increment();

    Meter meter = registry.get("my_gauge").tags("address", "_").meter();
    assertThat(meter.getId().getType()).isEqualTo(Type.GAUGE);
    Iterable<Measurement> measurements = meter.measure();
    assertThat(measurements).hasSize(1).first().satisfies(measurement -> {
      assertThat(measurement.getStatistic()).isEqualTo(Statistic.VALUE);
      assertThat(measurement.getValue()).isEqualTo(3d);
    });
    meter = registry.find("my_gauge").tags("address", "addr1").meter();
    assertThat(meter).isNull();
    meter = registry.find("my_gauge").tags("address", "addr2").meter();
    assertThat(meter).isNull();
  }

  @Test
  public void shouldSupportNoopGauges() {
    MeterRegistry registry = new SimpleMeterRegistry();
    registry.config().meterFilter(MeterFilter.deny(id -> "my_gauge".equals(id.getName())));
    CustomGauge g1 = new CustomGaugeBuilder("my_gauge", LongAdder::doubleValue).register(registry);
    g1.increment();

    assertThat(registry.find("my_gauge").meters()).isEmpty();
  }
}
