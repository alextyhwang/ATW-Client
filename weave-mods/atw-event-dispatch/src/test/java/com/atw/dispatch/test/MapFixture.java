// SPDX-License-Identifier: GPL-3.0-only
package com.atw.dispatch.test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import net.weavemc.api.event.EventBus;

/** Test-only access, never used by runtime delivery. Original subscriptions own every entry. */
final class MapFixture {
    private final Map<Class<?>, List<?>> map;
    @SuppressWarnings("unchecked")
    MapFixture() throws Exception {
        Field field = EventBus.class.getDeclaredField("map"); field.setAccessible(true);
        map = (Map<Class<?>, List<?>>) field.get(null);
    }
    void reset() { map.clear(); }
    Map<Class<?>, List<?>> state() { return map; }
}
