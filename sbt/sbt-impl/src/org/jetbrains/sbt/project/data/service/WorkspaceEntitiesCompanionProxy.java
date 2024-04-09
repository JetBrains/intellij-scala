package org.jetbrains.sbt.project.data.service;

import com.intellij.entities.SbtModuleEntity;
import com.intellij.entities.SharedSourcesOwnersEntity;
import com.intellij.platform.workspace.storage.EntitySource;

public class WorkspaceEntitiesCompanionProxy {
    public final static SbtModuleEntity.Companion SbtModuleEntityCompanion = SbtModuleEntity.Companion;
    public final static SharedSourcesOwnersEntity.Companion SharedSourcesOwnersEntityCompanion = SharedSourcesOwnersEntity.Companion;
    public static final EntitySource SharedSourcesOwnersEntitySource = com.intellij.entities.SharedSourcesOwnersEntitySource.INSTANCE;
}
