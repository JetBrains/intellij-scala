package org.jetbrains.sbt
package project.template

import java.io.File

import com.intellij.ide.util.projectWizard.{ModuleWizardStep, SdkSettingsStep, SettingsStep}
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalModuleBuilder
import com.intellij.openapi.externalSystem.settings.{AbstractExternalSystemSettings, ExternalSystemSettingsListener}
import com.intellij.openapi.externalSystem.util.{ExternalSystemUtil, ExternalSystemApiUtil}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.{JavaModuleType, ModifiableModuleModel}
import com.intellij.openapi.projectRoots.{JavaSdk, SdkTypeId}
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.io.FileUtil._
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings

/**
 * User: Dmitry Naydanov, Pavel Fatin
 * Date: 11/23/13
 */
class SbtModuleBuilder extends AbstractExternalModuleBuilder[SbtProjectSettings](SbtProjectSystem.Id, new SbtProjectSettings) {
  def getModuleType = JavaModuleType.getModuleType

  override def createModule(moduleModel: ModifiableModuleModel) = {
    val root = getModuleFileDirectory.toFile
    
    if (root.exists) {
      createProjectTemplateIn(root, getName)
      updateModulePath()
    }

    super.createModule(moduleModel)
  }

  // TODO customize the path in UI when IDEA-122951 will be implemented
  private def updateModulePath() {
    val file = getModuleFilePath.toFile
    val path = file.getParent + "/" + Sbt.ModulesDirectory + "/" + file.getName.toLowerCase
    setModuleFilePath(path)
  }

  override def modifySettingsStep(settingsStep: SettingsStep): ModuleWizardStep = {
    new SdkSettingsStep(settingsStep, this, new Condition[SdkTypeId] {
      def value(t: SdkTypeId): Boolean = t != null && t.isInstanceOf[JavaSdk]
    }) {
      override def updateDataModel() {
        settingsStep.getContext setProjectJdk myJdkComboBox.getSelectedJdk
      }
    }
  }

  private def createProjectTemplateIn(root: File, name: String) {
    val buildFile = root / Sbt.BuildFile
    val projectDir = root / Sbt.ProjectDirectory
    val pluginsFile = projectDir / Sbt.PluginsFile
    val propertiesFile = projectDir / Sbt.PropertiesFile

    if (!buildFile.createNewFile() ||
            !projectDir.mkdir() ||
            !pluginsFile.createNewFile()) return

    writeToFile(buildFile, SbtModuleBuilder.formatProjectDefinition(name))
    writeToFile(pluginsFile, SbtModuleBuilder.PluginsDefinition)
    writeToFile(propertiesFile, SbtModuleBuilder.SbtProperties)
  }

  override def getNodeIcon = Sbt.Icon

  override def setupRootModel(model: ModifiableRootModel) {
    val contentPath = getContentEntryPath
    if (StringUtil.isEmpty(contentPath)) return

    val contentRootDir = contentPath.toFile
    createDirectory(contentRootDir)

    val fileSystem = LocalFileSystem.getInstance
    val vContentRootDir = fileSystem.refreshAndFindFileByIoFile(contentRootDir)
    if (vContentRootDir == null) return

    model.addContentEntry(vContentRootDir)
    model.inheritSdk()
    val settings =
      ExternalSystemApiUtil.getSettings(model.getProject, SbtProjectSystem.Id).
        asInstanceOf[AbstractExternalSystemSettings[_ <: AbstractExternalSystemSettings[_, SbtProjectSettings, _],
        SbtProjectSettings, _ <: ExternalSystemSettingsListener[SbtProjectSettings]]]
//    model.commit()

    val externalProjectSettings = getExternalProjectSettings

    externalProjectSettings.setExternalProjectPath(getContentEntryPath)
    externalProjectSettings.setCreateEmptyContentRootDirectories(true) //create empty dirs anyway as src in our template is empty

    // TODO Add our SBT option checkboxes (auto-import, downloads) in the project wizard UI
    externalProjectSettings.resolveClassifiers = true
    externalProjectSettings.resolveSbtClassifiers = true
    externalProjectSettings.setUseAutoImport(true)

    settings.linkProject(externalProjectSettings)

    if (!externalProjectSettings.isUseAutoImport) {
      FileDocumentManager.getInstance.saveAllDocuments()
      ExternalSystemUtil.refreshProjects(model.getProject, SbtProjectSystem.Id, false)
    }
  }
}

// TODO Allow to specify Scala and SBT versions in the project wizard UI
private object SbtModuleBuilder {
  def formatProjectDefinition(name: String) =
    s"""name := "$name"
      |
      |version := "1.0"
      |
      |scalaVersion := "2.11.4"
    """.stripMargin
  
  def PluginsDefinition = "logLevel := Level.Warn"

  def SbtProperties = "sbt.version = 0.13.5"
}