// SPDX-License-Identifier: GPL-3.0-only
package com.atw.dispatch;

import java.lang.instrument.Instrumentation;
import net.weavemc.api.ModInitializer;
import net.weavemc.api.command.CommandBus;

/** Constructor and preInit deliberately do not resolve EventBus. */
public final class DispatchMod implements ModInitializer {
    @Override public void preInit(Instrumentation instrumentation) {
        System.out.println("[ATW dispatch] default OFF; strict first-definition Hook; /atwdispatch on|off|status|reset");
    }
    @Override public void init() { CommandBus.register(new DispatchCommand()); }
}
