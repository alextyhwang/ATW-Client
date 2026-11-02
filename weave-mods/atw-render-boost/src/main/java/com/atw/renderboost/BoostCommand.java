package com.atw.renderboost;

import net.weavemc.api.command.Command;
import net.minecraft.util.BlockPos;
import com.atw.renderboost.terrain.TerrainControl;

public final class BoostCommand extends Command {
    public BoostCommand() { super("atwboost", "atwrenderboost"); setExclusiveSuggestions(true); }
    @Override public void execute(String[] args) {
        String[] copy = args.clone();
        net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> executeOnRenderThread(copy));
    }
    private void executeOnRenderThread(String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "status";
        try {
            switch (action) {
                case "nameprep":
                    if(args.length>3)throw new IllegalArgumentException("nameprep on|off|status|clear");
                    String prepAction=args.length>2?args[2].toLowerCase(java.util.Locale.ROOT):"status";
                    if(prepAction.equals("on") || prepAction.equals("off")) {
                        RenderRuntime.cancelBenchmark("Name formatting preparation mode changed");
                        com.atw.renderboost.cache.NamePrepRuntime.request(prepAction.equals("on"));
                    }else if(prepAction.equals("clear")) {
                        RenderRuntime.cancelBenchmark("Name formatting preparation cache cleared");
                        com.atw.renderboost.cache.NamePrepRuntime.clear();
                    }else if(!prepAction.equals("status"))throw new IllegalArgumentException("nameprep on|off|status|clear");
                    RenderRuntime.message(com.atw.renderboost.cache.NamePrepRuntime.status());break;
                case "forward":
                    if(args.length>3)throw new IllegalArgumentException("forward on|off|status|clear");
                    String forwardAction=args.length>2?args[2].toLowerCase(java.util.Locale.ROOT):"status";
                    if(forwardAction.equals("on") || forwardAction.equals("off")) {
                        RenderRuntime.cancelBenchmark("Forward hover mode changed");
                        com.atw.renderboost.cache.ForwardHoverRuntime.request(forwardAction.equals("on"));
                    }else if(forwardAction.equals("clear")) {
                        RenderRuntime.cancelBenchmark("Forward hover cache cleared");
                        com.atw.renderboost.cache.ForwardHoverRuntime.clear();
                    }else if(!forwardAction.equals("status"))throw new IllegalArgumentException("forward on|off|status|clear");
                    RenderRuntime.message(com.atw.renderboost.cache.ForwardHoverRuntime.status());break;
                case "encoding":
                    if(args.length>3)throw new IllegalArgumentException("encoding on|off|status|clear");
                    String encodingAction=args.length>2?args[2].toLowerCase(java.util.Locale.ROOT):"status";
                    if(encodingAction.equals("on") || encodingAction.equals("off")) {
                        RenderRuntime.cancelBenchmark("Name encoding mode changed");
                        com.atw.renderboost.cache.NameEncodingRuntime.request(encodingAction.equals("on"));
                    }else if(encodingAction.equals("clear")) {
                        RenderRuntime.cancelBenchmark("Name encoding cache cleared");
                        com.atw.renderboost.cache.NameEncodingRuntime.clear();
                    }else if(!encodingAction.equals("status"))throw new IllegalArgumentException("encoding on|off|status|clear");
                    RenderRuntime.message(com.atw.renderboost.cache.NameEncodingRuntime.status());break;
                case "names":
                    if(args.length>3)throw new IllegalArgumentException("names on|off|status|clear");
                    String nameAction=args.length>2?args[2].toLowerCase(java.util.Locale.ROOT):"status";
                    if(nameAction.equals("on") || nameAction.equals("off")) {
                        RenderRuntime.cancelBenchmark("Name parse mode changed");
                        com.atw.renderboost.cache.NameParseRuntime.request(nameAction.equals("on"));
                    }else if(nameAction.equals("clear")) {
                        RenderRuntime.cancelBenchmark("Name parse cache cleared");
                        com.atw.renderboost.cache.NameParseRuntime.clear();
                    }else if(!nameAction.equals("status"))throw new IllegalArgumentException("names on|off|status|clear");
                    RenderRuntime.message(com.atw.renderboost.cache.NameParseRuntime.status());break;
                case "probe10":
                    if (args.length > 3) throw new IllegalArgumentException("probe10 counters|timing|status|stop");
                    RenderRuntime.nameProbe(args.length > 2 ? args[2].toLowerCase(java.util.Locale.ROOT) : "status");
                    break;
                case "terrain":
                    if (args.length > 3) throw new IllegalArgumentException("terrain on|off|status");
                    String terrainAction = args.length > 2 ? args[2].toLowerCase(java.util.Locale.ROOT) : "status";
                    if (terrainAction.equals("on") || terrainAction.equals("off")) {
                        RenderRuntime.cancelBenchmark("Terrain request changed");
                        TerrainControl.request(terrainAction.equals("on"));
                    } else if (!terrainAction.equals("status")) throw new IllegalArgumentException("terrain on|off|status");
                    RenderRuntime.message(TerrainControl.status());
                    break;
                case "on": RenderRuntime.setEnabled(true); break;
                case "off": RenderRuntime.setEnabled(false); break;
                case "toggle": RenderRuntime.setEnabled(!RenderRuntime.enabled()); break;
                case "clear": RenderRuntime.invalidate(); RenderRuntime.message("Cache invalidation queued."); break;
                case "bench":
                    if (args.length > 5) throw new IllegalArgumentException("bench [durationSeconds] [warmupSeconds] [stationary|moving]");
                    String motion = args.length > 4 ? args[4].toLowerCase(java.util.Locale.ROOT) : "stationary";
                    if (!motion.equals("stationary") && !motion.equals("moving"))
                        throw new IllegalArgumentException("Benchmark motion mode must be stationary or moving");
                    RenderRuntime.benchmark(args.length > 2 ? Integer.parseInt(args[2]) : 30,
                            args.length > 3 ? Integer.parseInt(args[3]) : 10, motion.equals("moving"));
                    break;
                case "cancel": RenderRuntime.cancelBenchmark("Cancelled by command"); break;
                case "status": RenderRuntime.message(RenderRuntime.status()); break;
                default: RenderRuntime.message("/atwboost on|off|toggle|clear|status|names on|off|status|clear|nameprep on|off|status|clear|encoding on|off|status|clear|forward on|off|status|clear|terrain on|off|status|probe10 counters|timing|status|stop|bench [30] [10] [stationary|moving]|cancel");
            }
        } catch (IllegalArgumentException | IllegalStateException e) { RenderRuntime.message(e.getMessage()); }
    }
    @Override public String[] getSuggestions(String[] args, BlockPos target) {
        if(args.length==2 && (args[0].equalsIgnoreCase("names") || args[0].equalsIgnoreCase("encoding") || args[0].equalsIgnoreCase("forward") || args[0].equalsIgnoreCase("nameprep")))
            return java.util.stream.Stream.of("on","off","status","clear")
                    .filter(s->s.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))).toArray(String[]::new);
        if (args.length == 2 && args[0].equalsIgnoreCase("terrain"))
            return java.util.stream.Stream.of("on", "off", "status")
                    .filter(s -> s.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))).toArray(String[]::new);
        if (args.length == 2 && args[0].equalsIgnoreCase("probe10"))
            return java.util.stream.Stream.of("counters", "timing", "status", "stop")
                    .filter(s -> s.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))).toArray(String[]::new);
        if (args.length != 1) return new String[0];
        return java.util.stream.Stream.of("on", "off", "toggle", "clear", "status", "names", "nameprep", "encoding", "forward", "terrain", "probe10", "bench", "cancel")
                .filter(s -> s.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toArray(String[]::new);
    }
}
