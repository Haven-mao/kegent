package com.haven.kegent.keep;

/** Prepares Keep's private record directory without changing any existing records. */
final class KeepLogDirectory {
    private KeepLogDirectory() {}

    static String prepareScript(String directory, String owner) {
        if (!owner.matches("[0-9]+:[0-9]+"))
            throw new IllegalArgumentException("无法确认 Keep 数据目录所属账号");
        String path = RootShell.quote(directory);
        // Normalize the directory itself on every attempt so a partially failed setup is retryable.
        // Refuse symlinks and non-directories; never replace a conflicting path or recurse into logs.
        return "if ! { test ! -L "
                + path
                + " && { test -d "
                + path
                + " || mkdir -m 700 "
                + path
                + "; } && chown "
                + owner
                + " "
                + path
                + " && chmod 700 "
                + path
                + " && restorecon "
                + path
                + "; }; then\n"
                + "echo KEGENT_LOG_DIRECTORY >&2; exit 23\n"
                + "fi\n";
    }
}
