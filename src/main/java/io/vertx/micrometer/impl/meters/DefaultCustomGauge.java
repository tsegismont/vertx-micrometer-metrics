package io.vertx.micrometer.impl.meters;

import java.util.concurrent.atomic.LongAdder;

class DefaultCustomGauge implements CustomGauge {
  private final LongAdder longAdder;

  public DefaultCustomGauge(LongAdder longAdder) {
    this.longAdder = longAdder;
  }

  @Override
  public void increment() {
    longAdder.increment();
  }

  @Override
  public void decrement() {
    longAdder.decrement();
  }

  @Override
  public void add(int value) {
    longAdder.add(value);
  }
}
