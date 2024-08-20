package org.jetbrains.sbt.project.template

import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalModuleBuilder
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.module.{JavaModuleType, ModifiableModuleModel, Module, ModuleType}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.plugins.scala.project.template.FileExt
import org.jetbrains.plugins.scala.util.ScalaPluginUtils
import org.jetbrains.sbt.Sbt

import java.io.File

abstract class ModuleBuilderBase[T <: ExternalProjectSettings](
  projectSystemId: ProjectSystemId,
  settings: T
) extends AbstractExternalModuleBuilder[T](
  projectSystemId,
  settings
) {

  protected val Log: Logger = Logger.getInstance(getClass)
  var openFileEditorAfterProjectOpened: Seq[VirtualFile] = Nil

  //TODO: why is it JavaModuleType and not SbtModuleType?
  override def getModuleType: ModuleType[_] = JavaModuleType.getModuleType

  /**
   * Reminder:<br>
   *  - `createModule` calls `setupModule`<br>
   *  - `setupModule` calls `setupRootModel`
   */
  override def createModule(moduleModel: ModifiableModuleModel): Module = {
    val root = new File(getModuleFileDirectory)

    if (ScalaPluginUtils.isRunningFromSources || ApplicationManager.getApplication.isUnitTestMode) {
      Log.assertTrue(root.exists(), "Module file directory should exist at this point")
    }

    if (root.exists()) {
      val moduleFilePathNew = moduleFilePathUpdated(getModuleFilePath)
      setModuleFilePath(moduleFilePathNew)
    }

    super.createModule(moduleModel)
  }

  override def setupModule(module: Module): Unit = {
    super.setupModule(module)
    Option(getContentEntryPath).foreach(ModuleBuilderUtil.tryToSetupModule(module, getExternalProjectSettings, _, projectSystemId))
  }

  override def setupRootModel(model: ModifiableRootModel): Unit = {
    for {
      contentPath <- Option(getContentEntryPath)
      contentDir = new File(contentPath)
      if FileUtilRt.createDirectory(contentDir)
    } {
      val contentEntryFolders = createProjectTemplateIn(contentDir)
      ModuleBuilderUtil.tryToSetupRootModel2(model, contentPath, contentEntryFolders)

      //execute when current dialog is closed
      openEditorForCodeSampleOrBuildFile(model.getProject, contentDir)
    }
  }

  private def openEditorForCodeSampleOrBuildFile(project: Project, contentDir: File): Unit = {
    //open code sample or externalSystemConfigFile
    val filesToOpen =
      if (openFileEditorAfterProjectOpened.nonEmpty)
        openFileEditorAfterProjectOpened
      else
        Option(VirtualFileManager.getInstance().findFileByNioPath((contentDir / externalSystemConfigFile).toPath)).toSeq

    if (filesToOpen.nonEmpty) {
      val psiManager = PsiManager.getInstance(project)
      filesToOpen.foreach { file =>
        Option(psiManager.findFile(file))
          .foreach { psiFile =>
            invokeLater {
              EditorHelper.openInEditor(psiFile)
            }
          }
      }
    }
  }

  protected def externalSystemConfigFile: String

  protected def createProjectTemplateIn(root: File): Option[DefaultModuleContentEntryFolders] = None

  // TODO customize the path in UI when IDEA-122951 will be implemented
  /**
   * By default module file points to the `projectRoot/moduleName.iml`.
   * We replace ("re-point") it to `projectRoot/.idea/modules/moduleName.iml`
   */
  private def moduleFilePathUpdated(pathname: String): String = {
    val file = new File(pathname)
    FileUtilRt.toSystemIndependentName(file.getParent) + "/" + Sbt.ModulesDirectory + "/" + file.getName
  }
}
