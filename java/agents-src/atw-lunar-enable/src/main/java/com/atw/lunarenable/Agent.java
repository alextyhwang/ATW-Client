package com.atw.lunarenable;

import java.lang.instrument.Instrumentation;

public final class Agent {
    private Agent() {
    }

    public static void premain(String option, Instrumentation instrumentation) {
        instrumentation.addTransformer(new MetadataTransformer());
        instrumentation.addTransformer(new BukkitTransformer());
    }
}
