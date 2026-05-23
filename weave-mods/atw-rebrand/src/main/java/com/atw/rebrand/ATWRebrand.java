package com.atw.rebrand;

import net.weavemc.loader.api.ModInitializer;

public class ATWRebrand implements ModInitializer {
    public static final String NAME = "ATW Client";
    public static final String LOG_PREFIX = "[ATW Rebrand] ";

    @Override
    public void preInit() {
        log("Loading ATW menu logo proof of concept.");
    }

    public static void log(String message) {
        System.out.println(LOG_PREFIX + message);
    }
}

