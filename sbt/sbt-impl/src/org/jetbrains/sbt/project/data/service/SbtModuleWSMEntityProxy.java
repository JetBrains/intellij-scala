package org.jetbrains.sbt.project.data.service;
import com.intellij.entities.SbtModuleWSMEntity;
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager;

public class SbtModuleWSMEntityProxy {
    public final static SbtModuleWSMEntity.Companion SbtModuleWSMEntityCompanion = SbtModuleWSMEntity.Companion;

    public final static VirtualFileUrlManager.Companion VirtualFileUrlManagerCompanion = VirtualFileUrlManager.Companion;
}
