/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.weavemc.api.ModInitializer
 */
package com.atw.rebrand;

import net.weavemc.api.ModInitializer;

public class ATWRebrand
implements ModInitializer {
    public static final String NAME = "ATW Client";
    public static final String LOG_PREFIX = "[ATW Rebrand] ";

    public void preInit(java.lang.instrument.Instrumentation instrumentation) {
        ATWRebrand.log("Loading ATW menu logo proof of concept.");
    }

    public static void log(String message) {
        System.out.println(LOG_PREFIX + message);
    }
}
