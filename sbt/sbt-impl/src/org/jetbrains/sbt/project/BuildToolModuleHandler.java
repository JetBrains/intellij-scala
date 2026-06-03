package org.jetbrains.sbt.project;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface BuildToolModuleHandler {
    ExtensionPointName<BuildToolModuleHandler> EP_NAME = ExtensionPointName.create("org.jetbrains.sbt.buildToolModuleHandler");

    boolean handles(Module module);

    @ApiStatus.Internal
    static boolean isHandledByBuildTool(Module module) {
        return EP_NAME.getExtensionList().stream().anyMatch(handler -> handler.handles(module));
    }
}
