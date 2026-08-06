package com.intellij.entities

import com.intellij.platform.workspace.storage.url.VirtualFileUrl

interface SbtModuleEntity : ModuleExtensionWorkspaceEntity {
  val sbtModuleId: String
  val buildURI: String
  val baseDirectory: VirtualFileUrl
}
