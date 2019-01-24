package org.jetbrains.bsp;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public interface Icons {
    Icon BSP = IconLoader.getIcon("/org/jetbrains/plugins/scala/bsp/images/buildServerProtocol.svg");
    Icon BSP_TARGET = IconLoader.getIcon("/org/jetbrains/plugins/scala/bsp/images/buildServerProtocolTarget.svg");
    @SuppressWarnings("unused") // used from BSP.xml
    Icon BSP_TOOLWINDOW = IconLoader.getIcon("/org/jetbrains/plugins/scala/bsp/images/toolWindowBuildServerProtocol.svg");
}
