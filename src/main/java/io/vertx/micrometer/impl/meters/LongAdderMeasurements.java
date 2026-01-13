package io.vertx.micrometer.impl.meters;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Statistic;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.ToDoubleFunction;

final class LongAdderMeasurements implements Iterable<Measurement> {

  private final LongAdder longAdder;
  private final LongAdderMeasurement measurement;

  LongAdderMeasurements(ToDoubleFunction<LongAdder> func) {
    longAdder = new LongAdder();
    measurement = new LongAdderMeasurement(longAdder, func != null ? func : LongAdder::doubleValue);
  }

  LongAdder longAdder() {
    return longAdder;
  }

  @Override
  public Iterator<Measurement> iterator() {
    return new MeasurementIterator(measurement);
  }

  private static class LongAdderMeasurement extends Measurement {
    public LongAdderMeasurement(LongAdder longAdder, ToDoubleFunction<LongAdder> func) {
      super(() -> func.applyAsDouble(longAdder), Statistic.VALUE);
    }
  }

  private static class MeasurementIterator implements Iterator<Measurement> {
    private final LongAdderMeasurement measurement;
    private boolean hasNext;

    MeasurementIterator(LongAdderMeasurement measurement) {
      this.measurement = measurement;
      hasNext = true;
    }

    @Override
    public boolean hasNext() {
      return hasNext;
    }

    @Override
    public Measurement next() {
      if (hasNext) {
        hasNext = false;
        return measurement;
      }
      throw new NoSuchElementException();
    }
  }
}
