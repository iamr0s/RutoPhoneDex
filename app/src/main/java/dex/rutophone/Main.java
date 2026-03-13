package dex.rutophone;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        try {
            int exitCode = run(args);
            if (exitCode != 0) {
                System.exit(exitCode);
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static int run(String[] args) throws ClassNotFoundException, InvocationTargetException,
            IllegalAccessException, NoSuchMethodException {
        if (args == null || args.length == 0 || isHelpCommand(args[0])) {
            printHelp();
            return 0;
        }
        if (isVersionCommand(args[0])) {
            printVersion();
            return 0;
        }

        String command = args[0];
        String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);

        if ("list-apps".equals(command)) {
            return handleListApps(commandArgs);
        }

        System.err.println("Unknown command: " + command);
        printHelp();
        return 2;
    }

    private static boolean isHelpCommand(String arg) {
        return "--help".equals(arg) || "-h".equals(arg) || "help".equals(arg);
    }

    private static boolean isVersionCommand(String arg) {
        return "--version".equals(arg) || "-v".equals(arg) || "version".equals(arg);
    }

    private static int handleListApps(String[] args) throws ClassNotFoundException, InvocationTargetException,
            IllegalAccessException, NoSuchMethodException {
        ParseResult parseResult = parseListAppsOptions(args);
        if (parseResult.helpRequested) {
            return 0;
        }
        if (parseResult.options == null) {
            return 2;
        }
        ListAppsOptions options = parseResult.options;

        Context context = NewProcessCompat.getUidContext();
        if (context == null) {
            System.err.println("getUIDContext returned null");
            return 3;
        }

        PackageManager packageManager = context.getPackageManager();
        List<PackageInfo> packages = getInstalledPackages(packageManager);
        packages.sort(Comparator.comparing(info -> info.packageName));

        int count = 0;
        for (PackageInfo info : packages) {
            ApplicationInfo appInfo = info.applicationInfo;
            boolean systemApp = appInfo != null && (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (!options.includeSystemApps && systemApp) {
                continue;
            }

            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("package", info.packageName);

            if (options.includeLabel) {
                CharSequence label = appInfo != null ? appInfo.loadLabel(packageManager) : info.packageName;
                fields.put("label", safeString(label));
            }
            if (options.includeUid) {
                fields.put("uid", String.valueOf(appInfo != null ? appInfo.uid : -1));
            }
            if (options.includeSystemFlag) {
                fields.put("is_system", String.valueOf(systemApp));
            }
            if (options.includeApkPath) {
                fields.put("apk_path", appInfo != null ? emptyToBlank(appInfo.sourceDir) : "");
            }

            System.out.println(formatFields(fields));
            count++;
        }

        System.out.println("count=" + count);
        return 0;
    }

    private static ParseResult parseListAppsOptions(String[] args) {
        ListAppsOptions options = new ListAppsOptions();
        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                printListAppsHelp();
                return ParseResult.help();
            }
            if ("--include-system".equals(arg)) {
                options.includeSystemApps = true;
                continue;
            }
            if ("--no-label".equals(arg)) {
                options.includeLabel = false;
                continue;
            }
            if ("--uid".equals(arg)) {
                options.includeUid = true;
                continue;
            }
            if ("--is-system".equals(arg)) {
                options.includeSystemFlag = true;
                continue;
            }
            if ("--apk-path".equals(arg)) {
                options.includeApkPath = true;
                continue;
            }

            System.err.println("Unknown option for list-apps: " + arg);
            printListAppsHelp();
            return ParseResult.error();
        }
        return ParseResult.success(options);
    }

    private static String formatFields(Map<String, String> fields) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!first) {
                builder.append(" | ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return builder.toString();
    }

    private static List<PackageInfo> getInstalledPackages(PackageManager packageManager) {
        if (Build.VERSION.SDK_INT >= getVersionCode("TIRAMISU", 33)) {
            try {
                Class<?> flagsClass = Class.forName("android.content.pm.PackageManager$PackageInfoFlags");
                Method ofMethod = flagsClass.getMethod("of", long.class);
                Object flags = ofMethod.invoke(null, 0L);
                Method getInstalledPackagesMethod =
                        PackageManager.class.getMethod("getInstalledPackages", flagsClass);
                @SuppressWarnings("unchecked")
                List<PackageInfo> packages =
                        (List<PackageInfo>) getInstalledPackagesMethod.invoke(packageManager, flags);
                return new ArrayList<>(packages);
            } catch (Throwable ignored) {
                // Fall back to the legacy API if the modern signature is unavailable.
            }
        }
        return new ArrayList<>(packageManager.getInstalledPackages(0));
    }

    private static int getVersionCode(String name, int fallback) {
        try {
            Field field = Build.VERSION_CODES.class.getField(name);
            return field.getInt(null);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String safeString(CharSequence value) {
        return value == null ? "" : value.toString().replace('\n', ' ').trim();
    }

    private static String emptyToBlank(String value) {
        return value == null ? "" : value;
    }

    private static void printHelp() {
        System.out.println("Usage:");
        System.out.println("  /system/bin/app_process -Djava.class.path=<apk-or-jar-path> /system/bin dex.rutophone.Main --help");
        System.out.println("  /system/bin/app_process -Djava.class.path=<apk-or-jar-path> /system/bin dex.rutophone.Main --version");
        System.out.println("  /system/bin/app_process -Djava.class.path=<apk-or-jar-path> /system/bin dex.rutophone.Main list-apps [options]");
        System.out.println();
        System.out.println("Version:");
        System.out.println("  " + BuildConfig.VERSION);
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  --help, -h     Show this help");
        System.out.println("  --version, -v  Show version");
        System.out.println("  list-apps      List installed packages");
        System.out.println();
        printListAppsHelp();
    }

    private static void printVersion() {
        System.out.println(BuildConfig.VERSION);
    }

    private static void printListAppsHelp() {
        System.out.println("list-apps options:");
        System.out.println("  --include-system  Include system apps");
        System.out.println("  --no-label        Exclude label field");
        System.out.println("  --uid             Include uid field");
        System.out.println("  --is-system       Include is_system field");
        System.out.println("  --apk-path        Include apk_path field");
    }

    private static final class ListAppsOptions {
        private boolean includeSystemApps;
        private boolean includeLabel = true;
        private boolean includeUid;
        private boolean includeSystemFlag;
        private boolean includeApkPath;
    }

    private static final class ParseResult {
        private final ListAppsOptions options;
        private final boolean helpRequested;

        private ParseResult(ListAppsOptions options, boolean helpRequested) {
            this.options = options;
            this.helpRequested = helpRequested;
        }

        private static ParseResult success(ListAppsOptions options) {
            return new ParseResult(options, false);
        }

        private static ParseResult help() {
            return new ParseResult(null, true);
        }

        private static ParseResult error() {
            return new ParseResult(null, false);
        }
    }

    private static final class NewProcessCompat {

        private static final String NEW_PROCESS_CLASS = "com.rosan.app_process.NewProcess";

        private NewProcessCompat() {
        }

        private static Context getUidContext() throws ClassNotFoundException, InvocationTargetException,
                IllegalAccessException, NoSuchMethodException {
            Class<?> newProcessClass = Class.forName(NEW_PROCESS_CLASS);

            for (Method method : newProcessClass.getDeclaredMethods()) {
                if (!"getUIDContext".equals(method.getName())) {
                    continue;
                }

                method.setAccessible(true);
                int parameterCount = method.getParameterCount();
                Object value;
                if (parameterCount == 0) {
                    value = method.invoke(null);
                } else if (parameterCount == 1 && method.getParameterTypes()[0] == int.class) {
                    value = method.invoke(null, android.os.Process.myUid());
                } else {
                    continue;
                }

                if (value instanceof Context) {
                    return (Context) value;
                }
            }

            throw new NoSuchMethodException("No compatible getUIDContext method found in " + NEW_PROCESS_CLASS);
        }
    }
}
