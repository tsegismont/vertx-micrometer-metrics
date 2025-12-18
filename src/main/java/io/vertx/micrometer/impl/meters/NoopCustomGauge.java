package io.vertx.micrometer.impl.meters;

enum NoopCustomGauge implements CustomGauge {
  INSTANCE;

  @Override
  public void increment() {
  }

  @Override
  public void decrement() {
  }

  @Override
  public void add(int value) {
  }
}
