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

package io.vertx.micrometer.impl.meters;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;

import java.util.concurrent.atomic.LongAdder;
import java.util.function.ToDoubleFunction;

public class CustomGaugeBuilder {

  private final Meter.Builder builder;

  public CustomGaugeBuilder(String name, ToDoubleFunction<LongAdder> func) {
    this.builder = Meter.builder(name, Meter.Type.GAUGE, new LongAdderMeasurements(func));
  }

  public CustomGaugeBuilder description(String description) {
    builder.description(description);
    return this;
  }

  public CustomGaugeBuilder tags(Iterable<Tag> tags) {
    builder.tags(tags);
    return this;
  }

  public CustomGauge register(MeterRegistry registry) {
    Iterable<Measurement> measurements = builder.register(registry).measure();
    if (measurements instanceof LongAdderMeasurements) {
      LongAdderMeasurements longAdderMeasurements = (LongAdderMeasurements) measurements;
      return new DefaultCustomGauge(longAdderMeasurements.longAdder());
    }
    return NoopCustomGauge.INSTANCE;
  }
}
