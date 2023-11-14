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
    Icon SBT_TOOL_WINDOW = findIcon("/org/jetbrains/sbt/images/sbtToolwin.svg", Icons.class);
    Icon SBT_SHELL_TOOL_WINDOW = findIcon("/org/jetbrains/sbt/images/sbtShellToolwin.svg", Icons.class);
}
