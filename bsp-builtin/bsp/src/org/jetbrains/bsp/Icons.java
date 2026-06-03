package org.jetbrains.bsp;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public interface Icons {
    Icon BSP = IconLoader.getIcon("/org/jetbrains/plugins/scala/bsp/images/buildServerProtocol.svg", Icons.class);
    Icon BSP_TARGET = IconLoader.getIcon("/org/jetbrains/plugins/scala/bsp/images/buildServerProtocolTarget.svg", Icons.class);
    //NOTE: there is no dedicated icon in old UI, however there is a dedicated icon in New UI
    //See BSPIconMappings.json and `buildServerProtocolLoadChanges.svg`
    Icon BSP_LOAD_CHANGES = IconLoader.getIcon("/org/jetbrains/plugins/scala/bsp/images/refresh.svg", Icons.class);
    @SuppressWarnings("unused") // used from scalaCommunity.bsp.xml
    Icon BSP_TOOLWINDOW = IconLoader.getIcon("/org/jetbrains/plugins/scala/bsp/images/toolWindowBuildServerProtocol.svg", Icons.class);
}
