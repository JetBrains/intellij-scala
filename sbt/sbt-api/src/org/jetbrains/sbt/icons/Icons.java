package org.jetbrains.sbt.icons;

import javax.swing.*;

import static com.intellij.openapi.util.IconLoader.findIcon;
import static com.intellij.openapi.util.IconLoader.getIcon;

public interface Icons {
    Icon SBT = getIcon("/org/jetbrains/sbt/images/sbtIcon.svg", Icons.class);
    Icon SBT_FOLDER = getIcon("/org/jetbrains/sbt/images/sbtFolder.svg", Icons.class);
    Icon SBT_FILE = getIcon("/org/jetbrains/sbt/images/sbtFile.svg", Icons.class);
    Icon SBT_SHELL = getIcon("/org/jetbrains/sbt/images/sbtShell.svg", Icons.class);
    Icon SBT_LOAD_CHANGES = getIcon("/org/jetbrains/sbt/images/sbtLoadChanges.svg", Icons.class);

    @SuppressWarnings("unused") // used from SBT.xml
    Icon SBT_TOOL_WINDOW = findIconNotNull("/org/jetbrains/sbt/images/sbtToolwin.svg");
    Icon SBT_SHELL_TOOL_WINDOW = findIconNotNull("/org/jetbrains/sbt/images/sbtShellToolwin.svg");

    private static Icon findIconNotNull(String path) {
        // SBT_TOOL_WINDOW and SBT_SHELL_TOOL_WINDOW may be used by idea in their @20x20 variants
        // unfortunately, IconLoader.getIcon() does not support this (see IDEA-338105)
        // but IconLoader.findIcon() does, but can return null
        Icon icon =  findIcon(path, Icons.class);
        assert(icon != null);
        return icon;
    }
}
