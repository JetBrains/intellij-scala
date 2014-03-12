package org.jetbrains.sbt
package project.module

import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalModuleBuilder
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import com.intellij.openapi.module.{JavaModuleType, Module, ModifiableModuleModel, ModuleType}
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import java.io.File
import com.intellij.openapi.util.io.{FileUtilRt, FileUtil}
import javax.swing.Icon
import org.jetbrains.sbt.project.SbtProjectSystem
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.settings.{ExternalSystemSettingsListener, AbstractExternalSystemSettings}

/**
 * User: Dmitry Naydanov
 * Date: 11/23/13
 */
class SbtModuleBuilder extends AbstractExternalModuleBuilder[SbtProjectSettings](SbtProjectSystem.Id, new SbtProjectSettings) {
  def getExternalProjectConfigFile(contentRootDir: VirtualFile): VirtualFile = null

  def getTemplateConfigName(settings: SbtProjectSettings): String = null

  def getModuleType: ModuleType[_ <: ModuleBuilder] = JavaModuleType.getModuleType

  override def createModule(moduleModel: ModifiableModuleModel): Module = {
    createSbtStub()
    super.createModule(moduleModel)
  }
  
  private def createSbtStub() {
    val modulePath = getModuleFileDirectory
    val moduleName = getName

    val dir = new File(modulePath)

    if (!dir.exists() || !dir.isDirectory) return
    
    val buildFile = new File(dir, "build.sbt")
    val projectDir = new File(dir, "project")
    val pluginsFile = new File(projectDir, "plugins.sbt")

    if (!buildFile.createNewFile() || !FileUtil.createDirectory(projectDir) || !pluginsFile.createNewFile()) return

    FileUtil.writeToFile(buildFile, SbtModuleBuilder buildSbt moduleName)
    FileUtil.writeToFile(pluginsFile, SbtModuleBuilder.pluginsSbt)
  }

  override def getNodeIcon: Icon = Sbt.Icon

  override def setupRootModel(model: ModifiableRootModel) {
    val contentPath = getContentEntryPath
    if (StringUtil isEmpty contentPath) return

    val contentRootDir = new File(contentPath)
    FileUtilRt createDirectory contentRootDir

    val fileSystem = LocalFileSystem.getInstance
    val vContentRootDir = fileSystem refreshAndFindFileByIoFile contentRootDir
    if (vContentRootDir == null) return

    model addContentEntry vContentRootDir
    model.inheritSdk()
    val settings =
      ExternalSystemApiUtil.getSettings(model.getProject, SbtProjectSystem.Id).
        asInstanceOf[AbstractExternalSystemSettings[_ <: AbstractExternalSystemSettings[_, SbtProjectSettings, _],
        SbtProjectSettings, _ <: ExternalSystemSettingsListener[SbtProjectSettings]]]
//    model.commit()

    val externalProjectSettings = getExternalProjectSettings

    externalProjectSettings setExternalProjectPath getContentEntryPath
    externalProjectSettings setCreateEmptyContentRootDirectories true //create empty dirs anyway as src in our template is empty

    settings linkProject externalProjectSettings
  }
}

object SbtModuleBuilder {
  def buildSbt(name: String) =
    s"""name := "$name"
      |
      |version := "1.0"
    """.stripMargin
  
  def pluginsSbt = "logLevel := Level.Warn"
}