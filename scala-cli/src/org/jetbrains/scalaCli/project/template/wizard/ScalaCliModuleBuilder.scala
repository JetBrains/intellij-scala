package org.jetbrains.scalaCli.project.template.wizard

import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalModuleBuilder
import com.intellij.openapi.module.{JavaModuleType, ModifiableModuleModel, Module, ModuleType}
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.{FileUtil, FileUtilRt}
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.ScalaVersion

import scala.sys.process._
import org.jetbrains.sbt.Sbt
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import com.intellij.psi.PsiManager
import org.jetbrains.bsp.BSP
import org.jetbrains.bsp.settings.BspProjectSettings
import org.jetbrains.plugins.scala.extensions._

import java.io.File
import org.jetbrains.sbt.project.template.{DefaultModuleContentEntryFolders, ModuleBuilderUtil, SbtModuleBuilderSelections}
import org.jetbrains.scalaCli.project.ScalaCliProjectUtils

import scala.sys.process.{Process, ProcessLogger}
import scala.util.Try

// TODO in this class there are few duplications with org.jetbrains.sbt.project.template.SbtModuleBuilderBase
class ScalaCliModuleBuilder (
  _selections: SbtModuleBuilderSelections
) extends AbstractExternalModuleBuilder[BspProjectSettings](
  BSP.ProjectSystemId,
  new BspProjectSettings
){ 

  private val selections = _selections.copy() // Selections is mutable data structure

  override def getModuleType: ModuleType[_] = JavaModuleType.getModuleType

  var openFileEditorAfterProjectOpened: Option[VirtualFile] = None


  override def createModule(moduleModel: ModifiableModuleModel): Module = {
    val moduleFilePathNew = moduleFilePathUpdated(getModuleFilePath)
    setModuleFilePath(moduleFilePathNew)

    super.createModule(moduleModel)
  }

  override def setupModule(module: Module): Unit = {
    super.setupModule(module)
    Option(getContentEntryPath).foreach(ModuleBuilderUtil.tryToSetupModule(module, getExternalProjectSettings, _, BSP.ProjectSystemId))
  }

  private def moduleFilePathUpdated(pathname: String): String = {
    val file = new File(pathname)
    FileUtilRt.toSystemIndependentName(file.getParent) + "/" + Sbt.ModulesDirectory + "/" + file.getName
  }

  override def setupRootModel(model: ModifiableRootModel): Unit = {
    for {
      contentPath <- Option(getContentEntryPath)
      contentDir = new File(contentPath)
      if FileUtilRt.createDirectory(contentDir)
    } {
      val scalaVersion = selections.scalaVersion.getOrElse(ScalaVersion.Latest.Scala_2_13.minor)
      val contentEntryFolders = createProjectTemplateIn(contentDir, scalaVersion)
      ModuleBuilderUtil.tryToSetupRootModel2(model, contentPath, contentEntryFolders)

      //execute when current dialog is closed
      openEditorForCodeSampleOrBuildFile(model.getProject, contentDir)
    }
  }


  private def openEditorForCodeSampleOrBuildFile(project: Project, contentDir: File): Unit = {
    //open code sample or buildSbt
    val fileToOpen = openFileEditorAfterProjectOpened.orElse {
      Option(VirtualFileManager.getInstance().findFileByNioPath((contentDir / Sbt.BuildFile).toPath))
    }
    fileToOpen.foreach { vFile =>
      val psiManager = PsiManager.getInstance(project)
      val psiFile = psiManager.findFile(vFile)
      if (psiFile != null) {
        invokeLater {
          EditorHelper.openInEditor(psiFile)
        }
      }
    }
  }

  private def createProjectTemplateIn(
    root: File,
    @NonNls scalaVersion: String,
  ): Option[DefaultModuleContentEntryFolders] = {
    val buildFile = root / ScalaCliProjectUtils.ProjectDefinitionFileName

    if (buildFile.createNewFile()) {
      val projectFileContent =
        s"""
           |//> using scala $scalaVersion
           |""".stripMargin

      def ensureSingleNewLineAfter(text: String): String = text.stripTrailing() + "\n"

      FileUtil.writeToFile(buildFile, ensureSingleNewLineAfter(projectFileContent))

      setUpBspDirectoryForScalaCli(root)

      Some(DefaultModuleContentEntryFolders(
        Seq(),
        Seq(),
        Nil,
        Nil,
      ))
    }
    else None
  }

  private def setUpBspDirectoryForScalaCli(workspace: File): Try[Int] = {
    val command = "scala-cli setup-ide ."
    // TODO add reporter
    // reporter.log(BspBundle.message("bsp.resolver.installing.mill.configuration.command", millCommand))
    Try(Process(command, workspace)! ProcessLogger(stdout append _ + "\n", stderr append _ + "\n"))
  }
}
