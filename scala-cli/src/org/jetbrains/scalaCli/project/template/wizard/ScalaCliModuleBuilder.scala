package org.jetbrains.scalaCli.project.template.wizard

import com.intellij.openapi.module.Module
import org.jetbrains.bsp.BSP
import org.jetbrains.bsp.settings.{BspProjectSettings, PreImportConfig}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.template.DefaultModuleContentEntryFolders
import org.jetbrains.sbt.project.template.{ModuleBuilderBase, ScalaModuleBuilderSelections}
import org.jetbrains.scalaCli.project.ScalaCliProjectUtils

import java.nio.file.{FileAlreadyExistsException, Files, Path}

class ScalaCliModuleBuilder (
  _selections: ScalaModuleBuilderSelections
) extends ModuleBuilderBase[BspProjectSettings](
  BSP.ProjectSystemId,
  new BspProjectSettings
){

  private val selections = _selections.copy() // Selections is mutable data structure

  override protected def externalSystemConfigFile: String =
    ScalaCliProjectUtils.ProjectDefinitionFileName

  override def setupModule(module: Module): Unit = {
    val settings = getExternalProjectSettings
    settings.bspConfigGenerated = true
    settings.preImportConfig = PreImportConfig.NoPreImport

    super.setupModule(module)
  }

  private def createNewFile(path: Path): Boolean =
    try {
      Files.createFile(path)
      true
    } catch {
      case _: FileAlreadyExistsException => false
    }

  override def createProjectTemplateIn(root: Path): Option[DefaultModuleContentEntryFolders] = {
    val buildFile = root / ScalaCliProjectUtils.ProjectDefinitionFileName

    if (createNewFile(buildFile)) {
      val scalaVersion = selections.scalaVersion.getOrElse(ScalaVersion.Latest.Scala_2_13.minor)
      val projectFileContent =
        s"//> using scala $scalaVersion"

      def ensureSingleNewLineAfter(text: String): String = text.stripTrailing() + "\n"

      Files.writeString(buildFile, ensureSingleNewLineAfter(projectFileContent))

      Some(DefaultModuleContentEntryFolders(
        sources = Seq(),
        testSources = Seq(),
        resources = Seq(),
        testResources = Nil,
        // TODO consider excluding .scala-build directory
        excluded = Seq(".bsp"),
      ))
    }
    else None
  }
}
