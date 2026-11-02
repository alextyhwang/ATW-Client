// Checks ATW's accounts.json entry against the Lunar build in the package, using
// Lunar's own account classes and bytecode. Run by test_lunar_account_contract.ps1.
//
// What failed before: ATW wrote "refreshToken": "". At startup Lunar's account
// manager refreshes every account whose refresh token is non-null, so it posted
// an empty refresh token to login.live.com, got an error, set the access token
// and refresh token to null and dropped the account ("Going to invalidate this
// account", "Current account is null?!"). Lunar's assets authenticator 403 is a
// separate service and never touches account tokens.
//
// The Lunar names below are obfuscated and specific to the pinned lunar.jar.
// Each one is checked against a readable anchor string first; if Lunar is
// updated, this test fails with "contract anchor" and must be re-derived.

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class LunarAccountContractTest {
    static final String P = "com/moonsworth/lunar/client/";
    static final String ACCOUNT = P + "HHHHCCCHOOHIHHIOOIRICCIOIIIIIR/CIHOOIHOIROIIRCORCCHIOIIOCICCH/CIHOOIHOIROIIRCORCCHIOIIOCICCH";
    static final String XBOX_ACCOUNT = P + "HHHHCCCHOOHIHHIOOIRICCIOIIIIIR/CIHOOIHOIROIIRCORCCHIOIIOCICCH/CHIRCOROIOHCCIIRRCCHOIHHHIOIOH";
    static final String MANAGER = P + "HHHHCCCHOOHIHHIOOIRICCIOIIIIIR/OCCOIRIHCIHOCIOCICROCRHRHRIHCO/CIHOOIHOIROIIRCORCCHIOIIOCICCH";
    static final String MS_REFRESH = P + "IIOICIIORRIICOHHOCHCICOHHIRHOO/CIRIIHOCHOHHOCRRRHORICIIOHOIHC";
    static final String GET_REFRESH_TOKEN = "ORIIIRHIICRRHHHCIROHIHIOCHIIRI";
    static final String SET_ACCESS_TOKEN = "IOIOHOHHCIHIHOHOHHIICOHHRHHCRO";
    static final String SET_REFRESH_TOKEN = "OROOCRRRICCRCORCIHIIHIRRHORHRO";
    static final String REFRESH = "RRCOIIORCHOIRRORHROICHCROIHHRR";
    static final String IS_VALID = "OCCCRHHHIIHCOOCCCRHHIHCORIHORR";
    static final String EXPIRES_MS = "ICHCCOICICRIHHHCOICOCCIRHHIIHC";

    static int failures = 0;

    static void check(boolean ok, String name) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (!ok) failures++;
    }

    public static void main(String[] args) throws Exception {
        Path fixture = Path.of(args[0]);
        List<Path> jars = new ArrayList<>();
        for (int i = 1; i < args.length; i++) jars.add(Path.of(args[i]));
        Map<String, ClassNode> classes = readClasses(jars);

        anchors(classes);
        if (failures > 0) {
            System.out.println("Lunar contract anchors changed; re-derive this test for the new lunar.jar.");
            System.exit(2);
        }

        JsonObject root = JsonParser.parseString(Files.readString(fixture)).getAsJsonObject();
        String active = root.get("activeAccountLocalId").getAsString();
        JsonObject entry = root.getAsJsonObject("accounts").getAsJsonObject(active);
        check(!entry.has("refreshToken"), "ATW entry has no refreshToken key");
        check("Xbox".equalsIgnoreCase(entry.get("type").getAsString()), "ATW entry type is Xbox (Lunar drops other types)");

        // Lunar's own account class reads the ATW entry.
        Object account = loadLunarAccount(active, entry);
        check(call(account, GET_REFRESH_TOKEN) == null, "Lunar reads no refresh token, so its startup refresh skips the account");
        check(entry.get("accessToken").getAsString().equals(call(account, "getAccessToken")),
              "Lunar keeps ATW's access token");
        check(Boolean.TRUE.equals(call(account, IS_VALID)), "Lunar treats the ATW token as valid");
        long expiresMs = (Long) call(account, EXPIRES_MS);
        check(expiresMs > System.currentTimeMillis(), "Lunar reads a future exp claim from the token");

        // Control: the old entry. A non-null refresh token reaches the refresh request.
        JsonObject old = entry.deepCopy();
        old.addProperty("refreshToken", "");
        Object oldAccount = loadLunarAccount(active, old);
        check("".equals(call(oldAccount, GET_REFRESH_TOKEN)),
              "control: an empty refreshToken is non-null to Lunar, so it would be refreshed and invalidated");

        managerSkipsNullRefreshToken(classes.get(MANAGER));
        refreshPostsEmptyToken(classes.get(MS_REFRESH));
        onlyRefreshInvalidates(classes);

        if (failures > 0) {
            System.out.println(failures + " check(s) failed.");
            System.exit(1);
        }
        System.out.println("Lunar account contract checks passed.");
    }

    static void anchors(Map<String, ClassNode> classes) {
        check(hasString(classes.get(ACCOUNT), "No access token found for [%s]"), "contract anchor: Lunar account class");
        ClassNode xbox = classes.get(XBOX_ACCOUNT);
        check(xbox != null && ACCOUNT.equals(xbox.superName) && xbox.methods.stream().anyMatch(m -> {
                  for (AbstractInsnNode insn : m.instructions)
                      if (insn instanceof FieldInsnNode f && f.name.equals("XBOX")) return true;
                  return false;
              }), "contract anchor: Lunar Xbox account class");
        check(hasString(classes.get(MANAGER), "Starting Refreshing %s with %sms till expired (Valid: %s)."), "contract anchor: Lunar account manager");
        check(hasString(classes.get(MS_REFRESH), "There was an error with refreshing the account. Going to invalidate this account."),
              "contract anchor: Lunar Microsoft refresh");
        check(hasMethod(classes.get(ACCOUNT), GET_REFRESH_TOKEN, "()Ljava/lang/String;")
                  && hasMethod(classes.get(ACCOUNT), SET_ACCESS_TOKEN, "(Ljava/lang/String;)V")
                  && hasMethod(classes.get(ACCOUNT), SET_REFRESH_TOKEN, "(Ljava/lang/String;)V"),
              "contract anchor: Lunar account token accessors");
    }

    // Manager: `if (account.getRefreshToken() != null) account.refresh(...)`.
    static void managerSkipsNullRefreshToken(ClassNode manager) {
        boolean nonNullRefreshes = false;
        boolean nullSkips = false;
        for (MethodNode method : manager.methods) {
            if (!hasString(method, "Starting Refreshing %s with %sms till expired (Valid: %s).")) continue;
            for (AbstractInsnNode insn : method.instructions) {
                if (!isCall(insn, GET_REFRESH_TOKEN) || next(insn) == null || next(insn).getOpcode() != Opcodes.IFNONNULL)
                    continue;
                nonNullRefreshes = branchCalls(((JumpInsnNode) next(insn)).label, REFRESH);
                nullSkips = !branchCalls(next(next(insn)), REFRESH);
            }
        }
        check(nonNullRefreshes && nullSkips, "Lunar's startup refresh runs only when the refresh token is non-null");
    }

    // Refresh: `if (getRefreshToken() == null) fail; else POST refresh_token=<value>`.
    static void refreshPostsEmptyToken(ClassNode refresh) {
        boolean nullOnly = false;
        for (MethodNode method : refresh.methods) {
            if (!hasString(method, "No valid refresh token, not attempting refresh.")) continue;
            for (AbstractInsnNode insn : method.instructions)
                if (isCall(insn, GET_REFRESH_TOKEN) && next(insn) != null && next(insn).getOpcode() == Opcodes.IFNONNULL)
                    nullOnly = true;
            nullOnly &= !callsAny(method, Set.of("isEmpty", "isBlank"));
        }
        check(nullOnly, "Lunar's refresh checks only for null, so an empty token is sent to login.live.com");
    }

    // Only the Microsoft refresh error path clears account tokens, and only the
    // account manager and the Xbox login start a refresh. The assets
    // authenticator that returns the 403 is neither, so a 403 cannot invalidate.
    static void onlyRefreshInvalidates(Map<String, ClassNode> classes) {
        Set<String> clearers = new TreeSet<>();
        Set<String> refreshers = new TreeSet<>();
        Set<String> authenticators = new TreeSet<>();
        for (ClassNode node : classes.values()) {
            if (hasString(node, "Connecting to Authenticator to obtain JWT")) authenticators.add(node.name);
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode insn : method.instructions) {
                    if (!(insn instanceof MethodInsnNode call) || !isAccountOwner(call.owner)) continue;
                    boolean setter = (call.name.equals(SET_ACCESS_TOKEN) || call.name.equals(SET_REFRESH_TOKEN))
                        && call.desc.equals("(Ljava/lang/String;)V");
                    if (setter && prev(insn) != null && prev(insn).getOpcode() == Opcodes.ACONST_NULL)
                        clearers.add(node.name);
                    if (call.name.equals(REFRESH) && call.desc.equals("(Ljava/util/function/Consumer;)V"))
                        refreshers.add(node.name);
                }
            }
        }
        check(clearers.equals(Set.of(MS_REFRESH)), "only Lunar's Microsoft refresh clears account tokens: " + clearers);
        check(!refreshers.isEmpty() && refreshers.stream().allMatch(n -> n.equals(XBOX_ACCOUNT) || n.startsWith(MANAGER)),
              "only the account manager and Xbox login start a refresh: " + refreshers);
        check(!authenticators.isEmpty() && Collections.disjoint(authenticators, clearers)
                  && Collections.disjoint(authenticators, refreshers),
              "Lunar's assets authenticator (the 403 service) neither refreshes nor clears accounts: " + authenticators);
    }

    // Lunar's static initializers start its logging and services, which need the
    // running game. This loader defines Lunar classes (except enums) with empty
    // static initializers; every other method, including load() and the token
    // getters under test, is Lunar's unmodified bytecode.
    static final ClassLoader LUNAR = new ClassLoader(LunarAccountContractTest.class.getClassLoader()) {
        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("com.moonsworth.lunar.")) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded != null) return loaded;
                try (var in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                    if (in == null) throw new ClassNotFoundException(name);
                    ClassNode node = new ClassNode();
                    new ClassReader(in.readAllBytes()).accept(node, 0);
                    boolean isEnum = "java/lang/Enum".equals(node.superName);
                    for (MethodNode method : node.methods) {
                        if (isEnum || !method.name.equals("<clinit>")) continue;
                        method.instructions.clear();
                        method.instructions.add(new InsnNode(Opcodes.RETURN));
                        method.tryCatchBlocks.clear();
                        method.localVariables = null;
                        method.maxStack = 0;
                        method.maxLocals = 0;
                    }
                    org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(0);
                    node.accept(writer);
                    byte[] bytes = writer.toByteArray();
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (java.io.IOException e) {
                    throw new ClassNotFoundException(name, e);
                }
            }
        }
    };

    static Object loadLunarAccount(String localId, JsonObject entry) throws Exception {
        Class<?> type = Class.forName(XBOX_ACCOUNT.replace('/', '.'), true, LUNAR);
        Object account = type.getConstructor(String.class).newInstance(localId);
        type.getMethod("load", JsonObject.class).invoke(account, entry);
        return account;
    }

    static Object call(Object target, String name) throws Exception {
        Method method = target.getClass().getMethod(name);
        return method.invoke(target);
    }

    static Map<String, ClassNode> readClasses(List<Path> jars) throws Exception {
        Map<String, ClassNode> classes = new HashMap<>();
        for (Path jar : jars) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                    ZipEntry entry = e.nextElement();
                    if (!entry.getName().startsWith(P) || !entry.getName().endsWith(".class")) continue;
                    ClassNode node = new ClassNode();
                    new ClassReader(zip.getInputStream(entry).readAllBytes()).accept(node, ClassReader.SKIP_FRAMES);
                    classes.putIfAbsent(node.name, node);
                }
            }
        }
        return classes;
    }

    static boolean isAccountOwner(String owner) {
        return owner.equals(ACCOUNT) || owner.equals(XBOX_ACCOUNT);
    }

    static boolean isCall(AbstractInsnNode insn, String name) {
        return insn instanceof MethodInsnNode call && call.name.equals(name) && isAccountOwner(call.owner);
    }

    static boolean callsAny(MethodNode method, Set<String> names) {
        for (AbstractInsnNode insn : method.instructions)
            if (insn instanceof MethodInsnNode call && names.contains(call.name)) return true;
        return false;
    }

    // Straight-line code from `start` up to the next jump or return.
    static boolean branchCalls(AbstractInsnNode start, String name) {
        for (AbstractInsnNode insn = start; insn != null; insn = insn.getNext()) {
            if (isCall(insn, name)) return true;
            int op = insn.getOpcode();
            if (op == Opcodes.GOTO || (op >= Opcodes.IRETURN && op <= Opcodes.RETURN)) return false;
        }
        return false;
    }

    static AbstractInsnNode next(AbstractInsnNode insn) {
        AbstractInsnNode n = insn.getNext();
        while (n != null && n.getOpcode() < 0) n = n.getNext();
        return n;
    }

    static AbstractInsnNode prev(AbstractInsnNode insn) {
        AbstractInsnNode p = insn.getPrevious();
        while (p != null && p.getOpcode() < 0) p = p.getPrevious();
        return p;
    }

    static boolean hasMethod(ClassNode node, String name, String desc) {
        return node != null && node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(desc));
    }

    static boolean hasString(ClassNode node, String text) {
        return node != null && node.methods.stream().anyMatch(m -> hasString(m, text));
    }

    static boolean hasString(MethodNode method, String text) {
        for (AbstractInsnNode insn : method.instructions)
            if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String s && s.contains(text)) return true;
        return false;
    }
}
