package io.vertx.micrometer.impl.meters;

public interface CustomGauge {
  void increment();

  void decrement();

  void add(int value);
}
