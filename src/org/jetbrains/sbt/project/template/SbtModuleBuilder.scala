package org.jetbrains.sbt
package project.template

import java.awt.FlowLayout
import java.awt.event.{ActionEvent, ActionListener}
import java.io.File
import javax.swing.border.EmptyBorder
import javax.swing.{Box, JCheckBox, JLabel, JPanel}

import com.intellij.ide.util.projectWizard.{ModuleBuilder, ModuleWizardStep, SdkSettingsStep, SettingsStep}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalModuleBuilder
import com.intellij.openapi.externalSystem.settings.{AbstractExternalSystemSettings, ExternalSystemSettingsListener}
import com.intellij.openapi.externalSystem.util.{ExternalSystemApiUtil, ExternalSystemUtil}
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.{JavaModuleType, ModifiableModuleModel, Module, ModuleType}
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.projectRoots.{JavaSdk, JavaSdkVersion, SdkTypeId}
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

  private var selectedScalaVersion: String = _

  private def scalaVersion = Option(selectedScalaVersion).getOrElse(Versions.DefaultScalaVersion)

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

    val sbtVersions = withProgressSynchronously("Fetching SBT versions")(_ => Versions.loadSbtVersions.filterNot(_.startsWith("0.12")))

    sbtVersionComboBox.setItems(sbtVersions)
    scalaPlatformComboBox.setItems(Platform.Values)

    scalaVersionComboBox.setTextRenderer(Version.abbreviate)

    scalaVersionComboBox.addActionListener(new ActionListener {
      override def actionPerformed(e: ActionEvent): Unit = selectedScalaVersion = scalaVersionComboBox.getSelectedItem.asInstanceOf[String]
    })

    def setupScalaVersionItems(): Unit = {
      def loadScalaVersions(): Array[String] = {
        def platform = scalaPlatformComboBox.getSelectedItem.asInstanceOf[Platform]
        withProgressSynchronously(s"Fetching ${platform.name} versions")(_ => Versions.loadScalaVersions(platform))
      }

      val loadedVersions = loadScalaVersions()
      scalaVersionComboBox.setItems(loadedVersions)
      for (v <- Option(selectedScalaVersion) if loadedVersions.contains(v)) {
        scalaVersionComboBox.setSelectedItem(v)
      }
    }

    setupScalaVersionItems()

    scalaPlatformComboBox.addActionListener(new ActionListener {
      override def actionPerformed(e: ActionEvent): Unit = {
        setupScalaVersionItems()
      }
    })

    val resolveClassifiersCheckBox = applyTo(new JCheckBox(SbtBundle("sbt.settings.sources")))(
      _.setToolTipText("Download Scala standard library sources (useful for editing the source code)")
    )

    val resolveSbtClassifiersCheckBox = applyTo(new JCheckBox(SbtBundle("sbt.settings.sources")))(
      _.setToolTipText("Download SBT sources (useful for editing the project definition)")
    )

    val step = new SdkSettingsStep(settingsStep, this, new Condition[SdkTypeId] {
      def value(t: SdkTypeId): Boolean = t != null && t.isInstanceOf[JavaSdk]
    }) {
      override def updateDataModel() {
        sbtVersion = sbtVersionComboBox.getSelectedItem.asInstanceOf[String]
        scalaPlatform = scalaPlatformComboBox.getSelectedItem.asInstanceOf[Platform]

        settingsStep.getContext setProjectJdk myJdkComboBox.getSelectedJdk

        getExternalProjectSettings.setResolveClassifiers(resolveClassifiersCheckBox.isSelected)
        getExternalProjectSettings.setResolveJavadocs(false)
        getExternalProjectSettings.setResolveSbtClassifiers(resolveSbtClassifiersCheckBox.isSelected)
        getExternalProjectSettings.setUseAutoImport(false)
        getExternalProjectSettings.setCreateEmptyContentRootDirectories(false)
      }

      override def validate(): Boolean = {
        if (!super.validate()) return false

        val selectedSdk = myJdkComboBox.getSelectedJdk
        def isJava8 = JavaSdk.getInstance().getVersion(selectedSdk).isAtLeast(JavaSdkVersion.JDK_1_8)

        val scalaVersion = scalaVersionComboBox.getSelectedItem.asInstanceOf[String]
        if (scalaVersion == null || selectedSdk == null) true
        else {
          val selectedVersion = Version(scalaVersion)
          val needJdk8 = selectedVersion >= Version("2.12") && !isJava8

          if (needJdk8) {
            throw new ConfigurationException("Scala 2.12 requires JDK 1.8", "Wrong JDK version")
          }
          else true
        }
      }
    }

    resolveClassifiersCheckBox.setSelected(true)
    resolveSbtClassifiersCheckBox.setSelected(false)

    val sbtVersionPanel = applyTo(new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)))(
      _.add(sbtVersionComboBox),
      _.add(resolveSbtClassifiersCheckBox)
    )

    val scalaVersionPanel = applyTo(new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)))(
      _.setBorder(new EmptyBorder(1, 0, 0, 0)),
      _.add(scalaPlatformComboBox),
      _.add(Box.createHorizontalStrut(4)),
      _.add(scalaVersionComboBox),
      _.add(resolveClassifiersCheckBox)
    )

    settingsStep.addSettingsField(SbtBundle("sbt.settings.sbt"), sbtVersionPanel)
    settingsStep.addSettingsField(SbtBundle("sbt.settings.scala"), scalaVersionPanel)

    // TODO Remove the label patching when the External System will use the concise and proper labels natively
    Option(sbtVersionPanel.getParent).foreach { parent =>
      parent.getComponents.toSeq.foreachDefined {
        case label: JLabel if label.getText == "Project SDK:" =>
          label.setText("JDK:")
          label.setDisplayedMnemonic('J')

        case label: JLabel if label.getText.startsWith("Project ") && label.getText.length > 8 =>
          label.setText(label.getText.substring(8) |> (s => s.substring(0, 1).toUpperCase + s.substring(1)))
      }
    }

    step
  }

  private def createProjectTemplateIn(root: File, name: String, platform: Platform, scalaVersion: String, sbtVersion: String) {
    val buildFile = root / Sbt.BuildFile
    val projectDir = root / Sbt.ProjectDirectory
    val propertiesFile = projectDir / Sbt.PropertiesFile

    if (!buildFile.createNewFile() ||
            !projectDir.mkdir()) return

    (root / "src" / "main" / "scala").mkdirs()
    (root / "src" / "test" / "scala").mkdirs()

    writeToFile(buildFile, SbtModuleBuilder.formatProjectDefinition(name, platform, scalaVersion))
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
        |scalaOrganization in updateSbtClassifiers := (scalaOrganization in Global).value
        |
        |ivyScala ~= (_ map (_ copy (overrideScalaVersion = false)))
        |
        |libraryDependencies += "ch.epfl.lamp" % "dotty_2.11" % scalaVersion.value % "scala-tool"
        |
        |scalaCompilerBridgeSource := ("ch.epfl.lamp" % "dotty-sbt-bridge" % scalaVersion.value % "component").sources()"""
        .stripMargin
  }

  def formatSbtProperties(sbtVersion: String) = s"sbt.version = $sbtVersion"
}
