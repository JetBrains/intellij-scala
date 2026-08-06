package org.jetbrains.plugins.scala.project.external;

import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId;
import com.intellij.workspaceModel.ide.impl.legacyBridge.LegacyBridgeModifiableBase;
import com.intellij.workspaceModel.ide.legacyBridge.LegacyBridgeJpsEntitySourceFactory;

public class CompanionProxyUtils {
    public final static LegacyBridgeJpsEntitySourceFactory.Companion LegacyBridgeJpsEntitySourceFactoryCompanion = LegacyBridgeJpsEntitySourceFactory.Companion;
    public final static LegacyBridgeModifiableBase.Companion LegacyBridgeModifiableBaseCompanion = LegacyBridgeModifiableBase.Companion;
    public final static LibraryRootTypeId.Companion LibraryRootTypeIdCompanion = LibraryRootTypeId.Companion;
}
