package org.jetbrains.sbt.language.utils

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.BuildSystemType
import org.jetbrains.sbt.project.SbtProjectSystem

object SbtCommon {
  val buildSystemType = new BuildSystemType(
    "SBT",
    "sbt")
  val libScopes = "Compile,Provided,Test"
  val defaultLibScope = "Compile"
  val scopeTerminology = "Configuration"

  def refreshSbtProject(project: Project): Unit = {
    FileDocumentManager.getInstance.saveAllDocuments()
    ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, SbtProjectSystem.Id))
  }
}
