/*
 * Copyright 2023 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.micrometer.impl;

import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.micrometer.Label;
import io.vertx.micrometer.MetricsDomain;
import io.vertx.micrometer.MetricsNaming;

import java.util.EnumSet;

/**
 * Abstract class for metrics container.
 *
 * @author Joel Takvorian
 */
public abstract class AbstractMetrics implements MicrometerMetrics {

  protected final MeterRegistry registry;
  protected final MetricsNaming names;
  private final String category;
  protected final EnumSet<Label> enabledLabels;

  AbstractMetrics(MeterRegistry registry, MetricsNaming names, EnumSet<Label> enabledLabels) {
    this.registry = registry;
    this.category = null;
    this.enabledLabels = enabledLabels;
    this.names = names;
  }

  AbstractMetrics(AbstractMetrics parent, MetricsDomain domain) {
    this(parent, domain == null ? null : domain.toCategory());
  }

  AbstractMetrics(AbstractMetrics parent, String category) {
    this.registry = parent.registry;
    this.enabledLabels = parent.enabledLabels;
    this.category = category;
    this.names = parent.names.withBaseName(baseName());
  }

  /**
   * @return the Micrometer registry used with this measured object.
   */
  @Override
  public MeterRegistry registry() {
    return registry;
  }

  // Method is final because it is invoked from the constructor
  @Override
  public final String baseName() {
    return category == null ? null : "vertx." + category + ".";
  }
}
