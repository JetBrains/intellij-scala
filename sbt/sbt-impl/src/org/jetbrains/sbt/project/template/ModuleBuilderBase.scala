package org.jetbrains.sbt.project.template

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
import org.jetbrains.annotations.{NotNull, TestOnly}
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.project.template.{DefaultModuleContentEntryFolders, ModuleBuilderUtil}
import org.jetbrains.plugins.scala.util.ScalaPluginUtils
import org.jetbrains.sbt.Sbt

import java.nio.file.{FileAlreadyExistsException, Files, Path}

abstract class ModuleBuilderBase[T <: ExternalProjectSettings](
  projectSystemId: ProjectSystemId,
  settings: T
) extends AbstractExternalModuleBuilder[T](
  projectSystemId,
  settings
) {

  protected val Log: Logger = Logger.getInstance(getClass)
  var openFileEditorAfterProjectOpened: Seq[VirtualFile] = Nil

  /**
   * ATTENTION: should be modified in tests only
   */
  @TestOnly
  var runExternalSystemProjectRefreshOnProjectOpen: Boolean = true

  //TODO: why is it JavaModuleType and not SbtModuleType?
  override def getModuleType: ModuleType[?] = JavaModuleType.getModuleType

  /**
   * Reminder:<br>
   *  - `createModule` calls `setupModule`<br>
   *  - `setupModule` calls `setupRootModel`
   */
  override def createModule(moduleModel: ModifiableModuleModel): Module = {
    val root = Path.of(getModuleFileDirectory)

    if (ScalaPluginUtils.isRunningFromSources || ApplicationManager.getApplication.isUnitTestMode) {
      Log.assertTrue(root.exists, "Module file directory should exist at this point")
    }

    if (root.exists) {
      val moduleFilePathNew = moduleFilePathUpdated(getModuleFilePath)
      setModuleFilePath(moduleFilePathNew)
    }

    super.createModule(moduleModel)
  }

  override def setupModule(module: Module): Unit = {
    super.setupModule(module)
    ModuleBuilderUtil.setRunExternalSystemProjectRefreshOnProjectOpen(module, runExternalSystemProjectRefreshOnProjectOpen)
    Option(getContentEntryPath).foreach { contentEntryPath =>
      ModuleBuilderUtil.tryToSetupModule(
        module = module,
        externalProjectSettings = getExternalProjectSettings,
        contentEntryPath = contentEntryPath,
        projectSystemId = projectSystemId
      )
    }
  }

  /**
   * Written to be similar to [[com.intellij.openapi.util.io.FileUtilRt#createDirectory(java.io.File)]], but for
   * [[java.nio.file.Path]].
   */
  private def createDirectory(@NotNull path: Path): Boolean = {
    def mkdirs(p: Path): Boolean =
      try {
        Files.createDirectories(p)
        true
      } catch {
        case _: FileAlreadyExistsException => false
      }

    path.isDirectory || mkdirs(path)
  }

  override def setupRootModel(model: ModifiableRootModel): Unit = {
    for {
      contentPath <- Option(getContentEntryPath)
      contentDir = Path.of(contentPath) if createDirectory(contentDir)
    } {
      val contentEntryFolders = createProjectTemplateIn(contentDir)
      ModuleBuilderUtil.tryToSetupRootModel2(model, contentPath, contentEntryFolders)

      //execute when current dialog is closed
      openEditorForCodeSampleOrBuildFile(model.getProject, contentDir)
    }
  }

  private def openEditorForCodeSampleOrBuildFile(project: Project, contentDir: Path): Unit = {
    //open code sample or externalSystemConfigFile
    val filesToOpen =
      if (openFileEditorAfterProjectOpened.nonEmpty)
        openFileEditorAfterProjectOpened
      else
        Option(VirtualFileManager.getInstance().findFileByNioPath(contentDir / externalSystemConfigFile)).toSeq

    ModuleBuilderUtil.openFilesInEditor(filesToOpen, project)
  }

  protected def externalSystemConfigFile: String

  protected def createProjectTemplateIn(root: Path): Option[DefaultModuleContentEntryFolders] = None

  // TODO customize the path in UI when IDEA-122951 will be implemented
  /**
   * By default module file points to the `projectRoot/moduleName.iml`.
   * We replace ("re-point") it to `projectRoot/.idea/modules/moduleName.iml`
   */
  private def moduleFilePathUpdated(pathname: String): String = {
    val file = Path.of(pathname)
    val wholePath = (file.getParent / Path.of(Sbt.ModulesDirectory) / file.getFileName).toCanonicalPath
    FileUtilRt.toSystemIndependentName(wholePath.toString)
  }
}
