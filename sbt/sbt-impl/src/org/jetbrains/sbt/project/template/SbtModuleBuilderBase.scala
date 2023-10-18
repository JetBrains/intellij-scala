package org.jetbrains.sbt.project.template

import com.intellij.ide.util.EditorHelper
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalModuleBuilder
import com.intellij.openapi.module.{JavaModuleType, ModifiableModuleModel, Module, ModuleType}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.{VirtualFile, VirtualFileManager}
import com.intellij.psi.PsiManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.extensions.invokeLater
import org.jetbrains.plugins.scala.project.template.FileExt
import org.jetbrains.plugins.scala.util.ScalaPluginUtils
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings

import java.io.File

@ApiStatus.Experimental
abstract class SbtModuleBuilderBase
  extends AbstractExternalModuleBuilder[SbtProjectSettings](
    SbtProjectSystem.Id,
    new SbtProjectSettings
  ) {

  protected val Log: Logger = Logger.getInstance(getClass)
  var openFileEditorAfterProjectOpened: Option[VirtualFile] = None

  //TODO: why is it JavaModuleType and not SbtModuleType?
  override def getModuleType: ModuleType[_ <: ModuleBuilder] = JavaModuleType.getModuleType

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
    Option(getContentEntryPath).foreach(SbtModuleBuilderUtil.tryToSetupModule(module, getExternalProjectSettings, _))
  }

  override def setupRootModel(model: ModifiableRootModel): Unit = {
    for {
      contentPath <- Option(getContentEntryPath)
      contentDir = new File(contentPath)
      if FileUtilRt.createDirectory(contentDir)
    } {
      val contentEntryFolders = createProjectTemplateIn(contentDir)
      SbtModuleBuilderUtil.tryToSetupRootModel2(model, contentPath, contentEntryFolders)

      //execute when current dialog is closed
      invokeLater {
        openEditorForCodeSampleOrBuildFile(model.getProject, contentDir)
      }
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
        EditorHelper.openInEditor(psiFile)
      }
    }
  }

  protected def createProjectTemplateIn(root: File): Option[DefaultModuleContentEntryFolders] = None

  // TODO customize the path in UI when IDEA-122951 will be implemented

  /**
   * By default module file points to the `projectRoot/moduleName.iml`.
   * We replace ("re-point") it to `projectRoot/.idea/modules/moduleName.iml`
   */
  protected def moduleFilePathUpdated(pathname: String): String = {
    val file = new File(pathname)
    FileUtilRt.toSystemIndependentName(file.getParent) + "/" + Sbt.ModulesDirectory + "/" + file.getName
  }
}
