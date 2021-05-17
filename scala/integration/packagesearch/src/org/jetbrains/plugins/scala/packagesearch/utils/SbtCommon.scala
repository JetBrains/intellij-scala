package org.jetbrains.plugins.scala.packagesearch.utils

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.extensibility.BuildSystemType
import org.jetbrains.plugins.scala.packagesearch.PackageSearchSbtBundle
import org.jetbrains.sbt.project.SbtProjectSystem

object SbtCommon {
  val buildSystemType = new BuildSystemType(
    PackageSearchSbtBundle.message("packagesearch.sbt.build.system.name"),
    PackageSearchSbtBundle.message("packagesearch.sbt.build.system.key"))
  val libScopes = "Compile,Test"
  val defaultLibScope = "Compile"
  val scopeTerminology = "Configuration"

  def refreshSbtProject(project: Project): Unit = {
    FileDocumentManager.getInstance.saveAllDocuments()
    ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, SbtProjectSystem.Id))
  }
}
