package org.jetbrains.sbt
package project.template

import java.io.File
import javax.swing.JCheckBox

import com.intellij.ide.util.projectWizard.{ModuleWizardStep, SdkSettingsStep, SettingsStep}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalModuleBuilder
import com.intellij.openapi.externalSystem.settings.{AbstractExternalSystemSettings, ExternalSystemSettingsListener}
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil, ExternalSystemBundle, ExternalSystemUtil}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.{JavaModuleType, ModifiableModuleModel}
import com.intellij.openapi.projectRoots.{JavaSdk, SdkTypeId}
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.io.FileUtil._
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.plugins.scala.extensions.withProgressSynchronously
import org.jetbrains.plugins.scala.project.Versions
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings

/**
 * User: Dmitry Naydanov, Pavel Fatin
 * Date: 11/23/13
 */
class SbtModuleBuilder extends AbstractExternalModuleBuilder[SbtProjectSettings](SbtProjectSystem.Id, new SbtProjectSettings) {
  private var sbtVersion = Versions.DefaultSbtVersion

  private var scalaVersion = Versions.DefaultScalaVersion

  def getModuleType = JavaModuleType.getModuleType

  override def createModule(moduleModel: ModifiableModuleModel) = {
    val root = getModuleFileDirectory.toFile

    if (root.exists) {
      createProjectTemplateIn(root, getName, scalaVersion, sbtVersion)
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
    val sbtVersionComboBox            = new SComboBox[String](Array.empty)
    val scalaVersionComboBox          = new SComboBox[String](Array.empty)

    val (scalaVersions, sbtVersions) = withProgressSynchronously("Fetching available versions") { listener =>
      listener("Fetching Scala versions...")
      val scalaVersions = Versions.loadScalaVersions
      listener("Fetching SBT versions...")
      val sbtVersions = Versions.loadSbtVersions
      (scalaVersions, sbtVersions)
    }

    sbtVersionComboBox.setItems(sbtVersions)
    scalaVersionComboBox.setItems(scalaVersions)

    val resolveClassifiersCheckBox    = new JCheckBox(SbtBundle("sbt.settings.resolveClassifiers"))
    val resolveSbtClassifiersCheckBox = new JCheckBox(SbtBundle("sbt.settings.resolveSbtClassifiers"))
    val useAutoImportCheckBox         = new JCheckBox(ExternalSystemBundle.message("settings.label.use.auto.import"))
    val createContentDirsCheckBox     = new JCheckBox(ExternalSystemBundle.message("settings.label.create.empty.content.root.directories"))
    val cachedUpdateCheckBox          = new JCheckBox(SbtBundle("sbt.settings.cacheUpdateResults"))

    val step = new SdkSettingsStep(settingsStep, this, new Condition[SdkTypeId] {
      def value(t: SdkTypeId): Boolean = t != null && t.isInstanceOf[JavaSdk]
    }) {
      override def updateDataModel() {
        sbtVersion = sbtVersionComboBox.getSelectedItem
        scalaVersion = scalaVersionComboBox.getSelectedItem

        settingsStep.getContext setProjectJdk myJdkComboBox.getSelectedJdk

        getExternalProjectSettings.setResolveClassifiers(resolveClassifiersCheckBox.isSelected)
        getExternalProjectSettings.setResolveSbtClassifiers(resolveSbtClassifiersCheckBox.isSelected)
        getExternalProjectSettings.setUseAutoImport(useAutoImportCheckBox.isSelected)
        getExternalProjectSettings.setCreateEmptyContentRootDirectories(createContentDirsCheckBox.isSelected)
        getExternalProjectSettings.setCachedUpdate(cachedUpdateCheckBox.isSelected)
      }
    }

    createContentDirsCheckBox.setSelected(true)
    resolveClassifiersCheckBox.setSelected(true)
    resolveSbtClassifiersCheckBox.setSelected(true)

    settingsStep.addSettingsField(SbtBundle("sbt.settings.sbtVersion"), sbtVersionComboBox)
    settingsStep.addSettingsField(SbtBundle("sbt.settings.scalaVersion"), scalaVersionComboBox)
    settingsStep.addSettingsField("", useAutoImportCheckBox)
    settingsStep.addSettingsField("", createContentDirsCheckBox)
    settingsStep.addSettingsField("", resolveClassifiersCheckBox)
    settingsStep.addSettingsField("", resolveSbtClassifiersCheckBox)
    settingsStep.addSettingsField("", cachedUpdateCheckBox)

    step
  }

  private def createProjectTemplateIn(root: File, name: String, scalaVersion: String, sbtVersion: String) {
    val buildFile = root / Sbt.BuildFile
    val projectDir = root / Sbt.ProjectDirectory
    val pluginsFile = projectDir / Sbt.PluginsFile
    val propertiesFile = projectDir / Sbt.PropertiesFile

    if (!buildFile.createNewFile() ||
            !projectDir.mkdir() ||
            !pluginsFile.createNewFile()) return

    writeToFile(buildFile, SbtModuleBuilder.formatProjectDefinition(name, scalaVersion))
    writeToFile(pluginsFile, SbtModuleBuilder.PluginsDefinition)
    writeToFile(propertiesFile, SbtModuleBuilder.formatSbtProperties(sbtVersion))
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
    settings.linkProject(externalProjectSettings)

    if (!externalProjectSettings.isUseAutoImport) {
      FileDocumentManager.getInstance.saveAllDocuments()
      ApplicationManager.getApplication.invokeLater(new Runnable() {
        override def run(): Unit =
          ExternalSystemUtil.refreshProjects(
            new ImportSpecBuilder(model.getProject, SbtProjectSystem.Id)
                    .forceWhenUptodate()
                    .use(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
          )
      })
    }
  }
}

// TODO Allow to specify Scala and SBT versions in the project wizard UI
private object SbtModuleBuilder {
  def formatProjectDefinition(name: String, scalaVersion: String) =
    s"""name := "$name"
      |
      |version := "1.0"
      |
      |scalaVersion := "$scalaVersion"
    """.stripMargin
  
  def PluginsDefinition = "logLevel := Level.Warn"

  def formatSbtProperties(sbtVersion: String) = s"sbt.version = $sbtVersion"
}