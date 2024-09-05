package org.jetbrains.scalaCli.project.template.wizard

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.plugins.scala.ScalaVersion

import org.jetbrains.bsp.BSP
import org.jetbrains.bsp.settings.BspProjectSettings
import org.jetbrains.plugins.scala.extensions._

import java.io.File
import org.jetbrains.sbt.project.template.{DefaultModuleContentEntryFolders, ModuleBuilderBase, ScalaModuleBuilderSelections}
import org.jetbrains.scalaCli.project.ScalaCliProjectUtils

class ScalaCliModuleBuilder (
  _selections: ScalaModuleBuilderSelections
) extends ModuleBuilderBase[BspProjectSettings](
  BSP.ProjectSystemId,
  new BspProjectSettings
){

  private val selections = _selections.copy() // Selections is mutable data structure

  override protected def externalSystemConfigFile: String = ScalaCliProjectUtils.ProjectDefinitionFileName

  override def createProjectTemplateIn(root: File): Option[DefaultModuleContentEntryFolders] = {
    val buildFile = root / ScalaCliProjectUtils.ProjectDefinitionFileName

    if (buildFile.createNewFile()) {
      val scalaVersion = selections.scalaVersion.getOrElse(ScalaVersion.Latest.Scala_2_13.minor)
      val projectFileContent =
        s"//> using scala $scalaVersion"

      def ensureSingleNewLineAfter(text: String): String = text.stripTrailing() + "\n"

      FileUtil.writeToFile(buildFile, ensureSingleNewLineAfter(projectFileContent))

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
