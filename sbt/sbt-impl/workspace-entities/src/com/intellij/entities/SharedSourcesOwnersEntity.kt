package com.intellij.entities

interface SharedSourcesOwnersEntity : ModuleExtensionWorkspaceEntity {
  val ownerModuleIds: List<String>
}
