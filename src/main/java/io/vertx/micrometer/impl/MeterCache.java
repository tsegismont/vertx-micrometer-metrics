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

package io.vertx.micrometer.impl;

import io.micrometer.core.instrument.Meter;
import io.netty.util.concurrent.FastThreadLocal;

import java.util.LinkedHashMap;
import java.util.Map;

public class MeterCache {

  private final FastThreadLocal<LRUCache> threadLocal;

  public MeterCache(int maxSize) {
    threadLocal = new FastThreadLocal<LRUCache>() {
      @Override
      protected LRUCache initialValue() {
        return new LRUCache(maxSize);
      }

      @Override
      protected void onRemoval(LRUCache value) {
        value.clear();
      }
    };
  }

  @SuppressWarnings("unchecked")
  public <T> T get(Meter.Id id) {
    return (T) threadLocal.get().get(id);
  }

  public void put(Meter.Id id, Object value) {
    threadLocal.get().put(id, value);
  }

  public void close() {
    threadLocal.remove();
  }

  private static class LRUCache extends LinkedHashMap<Meter.Id, Object> {

    final int maxSize;

    LRUCache(int maxSize) {
      super();
      this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<Meter.Id, Object> eldest) {
      return size() > maxSize;
    }
  }
}
