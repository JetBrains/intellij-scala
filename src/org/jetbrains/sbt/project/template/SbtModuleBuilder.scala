package org.jetbrains.sbt
package project.template

import java.awt.FlowLayout
import java.awt.event.{ActionEvent, ActionListener}
import java.io.File
import javax.swing.{Box, JCheckBox, JPanel}

import com.intellij.ide.util.projectWizard.{ModuleBuilder, ModuleWizardStep, SdkSettingsStep, SettingsStep}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalModuleBuilder
import com.intellij.openapi.externalSystem.settings.{AbstractExternalSystemSettings, ExternalSystemSettingsListener}
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil, ExternalSystemBundle, ExternalSystemUtil}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.{JavaModuleType, ModifiableModuleModel, Module, ModuleType}
import com.intellij.openapi.projectRoots.{JavaSdk, SdkTypeId}
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.io.FileUtil._
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.Platform.{Dotty, Scala}
import org.jetbrains.plugins.scala.project.{Platform, Version, Versions}
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings

/**
 * User: Dmitry Naydanov, Pavel Fatin
 * Date: 11/23/13
 */
class SbtModuleBuilder extends AbstractExternalModuleBuilder[SbtProjectSettings](SbtProjectSystem.Id, new SbtProjectSettings) {
  private var sbtVersion = Versions.DefaultSbtVersion

  private var scalaPlatform = Platform.Default

  private var scalaVersion = Versions.DefaultScalaVersion

  def getModuleType: ModuleType[_ <: ModuleBuilder] = JavaModuleType.getModuleType

  override def createModule(moduleModel: ModifiableModuleModel): Module = {
    val root = getModuleFileDirectory.toFile

    if (root.exists) {
      createProjectTemplateIn(root, getName, scalaPlatform, scalaVersion, sbtVersion)
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
    val sbtVersionComboBox            = new SComboBox()
    val scalaPlatformComboBox         = new SComboBox()
    val scalaVersionComboBox          = new SComboBox()

    val sbtVersions = withProgressSynchronously("Fetching SBT versions")(_ => Versions.loadSbtVersions)

    sbtVersionComboBox.setItems(sbtVersions)
    scalaPlatformComboBox.setItems(Platform.Values)

    scalaVersionComboBox.setTextRenderer(Version.abbreviate)

    def loadScalaVersions(): Array[String] = {
      def platform = scalaPlatformComboBox.getSelectedItem.asInstanceOf[Platform]
      withProgressSynchronously(s"Fetching ${platform.name} versions")(_ => Versions.loadScalaVersions(platform))
    }

    scalaVersionComboBox.setItems(loadScalaVersions())

    scalaPlatformComboBox.addActionListener(new ActionListener {
      override def actionPerformed(e: ActionEvent): Unit = {
        scalaVersionComboBox.setItems(loadScalaVersions())
      }
    })

    val resolveClassifiersCheckBox    = new JCheckBox(SbtBundle("sbt.settings.resolveClassifiers"))
    val resolveJavadocsCheckBox       = new JCheckBox(SbtBundle("sbt.settings.resolveJavadocs"))
    val resolveSbtClassifiersCheckBox = new JCheckBox(SbtBundle("sbt.settings.resolveSbtClassifiers"))
    val createContentDirsCheckBox     = new JCheckBox(ExternalSystemBundle.message("settings.label.create.empty.content.root.directories"))

    val step = new SdkSettingsStep(settingsStep, this, new Condition[SdkTypeId] {
      def value(t: SdkTypeId): Boolean = t != null && t.isInstanceOf[JavaSdk]
    }) {
      override def updateDataModel() {
        sbtVersion = sbtVersionComboBox.getSelectedItem.asInstanceOf[String]
        scalaPlatform = scalaPlatformComboBox.getSelectedItem.asInstanceOf[Platform]
        scalaVersion = scalaVersionComboBox.getSelectedItem.asInstanceOf[String]

        settingsStep.getContext setProjectJdk myJdkComboBox.getSelectedJdk

        getExternalProjectSettings.setResolveClassifiers(resolveClassifiersCheckBox.isSelected)
        getExternalProjectSettings.setResolveJavadocs(resolveJavadocsCheckBox.isSelected)
        getExternalProjectSettings.setResolveSbtClassifiers(resolveSbtClassifiersCheckBox.isSelected)
        getExternalProjectSettings.setUseAutoImport(false)
        getExternalProjectSettings.setCreateEmptyContentRootDirectories(createContentDirsCheckBox.isSelected)
      }
    }

    createContentDirsCheckBox.setSelected(true)
    resolveClassifiersCheckBox.setSelected(true)
    resolveJavadocsCheckBox.setSelected(false)
    resolveSbtClassifiersCheckBox.setSelected(false)

    val scalaVersionPanel = applyTo(new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)))(
      _.add(scalaPlatformComboBox),
      _.add(Box.createHorizontalStrut(4)),
      _.add(scalaVersionComboBox)
    )

    settingsStep.addSettingsField(SbtBundle("sbt.settings.sbtVersion"), sbtVersionComboBox)
    settingsStep.addSettingsField(SbtBundle("sbt.settings.scalaVersion"), scalaVersionPanel)
    settingsStep.addSettingsField("", createContentDirsCheckBox)

    val downloadPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0))
    downloadPanel.add(resolveClassifiersCheckBox)
    downloadPanel.add(resolveJavadocsCheckBox)
    downloadPanel.add(resolveSbtClassifiersCheckBox)
    settingsStep.addSettingsField("Download:", downloadPanel)

    step
  }

  private def createProjectTemplateIn(root: File, name: String, platform: Platform, scalaVersion: String, sbtVersion: String) {
    val buildFile = root / Sbt.BuildFile
    val projectDir = root / Sbt.ProjectDirectory
    val pluginsFile = projectDir / Sbt.PluginsFile
    val propertiesFile = projectDir / Sbt.PropertiesFile

    if (!buildFile.createNewFile() ||
            !projectDir.mkdir() ||
            !pluginsFile.createNewFile()) return

    writeToFile(buildFile, SbtModuleBuilder.formatProjectDefinition(name, platform, scalaVersion))
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

private object SbtModuleBuilder {
  def formatProjectDefinition(name: String, platform: Platform, scalaVersion: String): String = platform match {
    case Scala =>
      s"""name := "$name"
         |
         |version := "1.0"
         |
         |scalaVersion := "$scalaVersion"
        """
        .stripMargin

    case Dotty =>
      s"""scalaVersion := "$scalaVersion"
        |
        |scalaOrganization := "ch.epfl.lamp"
        |
        |scalaBinaryVersion := "2.11"
        |
        |ivyScala ~= (_ map (_ copy (overrideScalaVersion = false)))
        |
        |libraryDependencies += "ch.epfl.lamp" % "dotty_2.11" % scalaVersion.value % "scala-tool"
        |
        |scalaCompilerBridgeSource := ("ch.epfl.lamp" % "dotty-sbt-bridge" % scalaVersion.value % "component").sources()"""
        .stripMargin
  }

  
  def PluginsDefinition = "logLevel := Level.Warn"

  def formatSbtProperties(sbtVersion: String) = s"sbt.version = $sbtVersion"
}