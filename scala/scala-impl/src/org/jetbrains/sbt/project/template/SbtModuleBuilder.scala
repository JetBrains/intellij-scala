package org.jetbrains.sbt
package project.template

import java.awt.FlowLayout
import java.io.File

import com.intellij.ide.util.projectWizard.{ModuleBuilder, ModuleWizardStep, SdkSettingsStep, SettingsStep}
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalModuleBuilder
import com.intellij.openapi.module.{JavaModuleType, ModifiableModuleModel, Module, ModuleType}
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.projectRoots.{JavaSdk, JavaSdkVersion, SdkTypeId}
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.io.FileUtil._
import javax.swing._
import javax.swing.border.EmptyBorder
import org.jetbrains.plugins.scala.extensions.JComponentExt.ActionListenersOwner
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.Platform.{Dotty, Scala}
import org.jetbrains.plugins.scala.project.{Platform, Version, Versions}
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.project.template.SbtModuleBuilderUtil._

import scala.collection.mutable

/**
 * User: Dmitry Naydanov, Pavel Fatin
 * Date: 11/23/13
 */
class SbtModuleBuilder extends AbstractExternalModuleBuilder[SbtProjectSettings](SbtProjectSystem.Id, new SbtProjectSettings) {
  private class Selections(var sbtVersion: String = null,
                           var scalaPlatform: Platform = Platform.Scala,
                           var scalaVersion: String = null,
                           var resolveClassifiers: Boolean = true,
                           var resolveSbtClassifiers: Boolean = false)

  private val selections = new Selections()

  private lazy val sbtVersions: Array[String] = withProgressSynchronously("Fetching sbt versions") {
    Versions.loadSbtVersions
  }

  private val scalaVersions: mutable.Map[Platform, Array[String]] = mutable.Map.empty

  private def loadedScalaVersions(platform: Platform) = scalaVersions.getOrElseUpdate(platform, {
    withProgressSynchronously(s"Fetching ${platform.getName} versions") {
      Versions.loadScalaVersions(platform)
    }
  })

  getExternalProjectSettings.setResolveJavadocs(false)
  getExternalProjectSettings.setUseAutoImport(false)
  getExternalProjectSettings.setCreateEmptyContentRootDirectories(false)

  def getModuleType: ModuleType[_ <: ModuleBuilder] = JavaModuleType.getModuleType

  override def createModule(moduleModel: ModifiableModuleModel): Module = {
    val root = getModuleFileDirectory.toFile

    if (root.exists) {
      import selections._

      getExternalProjectSettings.setResolveClassifiers(resolveClassifiers)
      getExternalProjectSettings.setResolveSbtClassifiers(resolveSbtClassifiers)
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
    setupDefaultVersions()

    val sbtVersionComboBox            = applyTo(new SComboBox())(
      _.setItems(sbtVersions),
      _.setSelectedItem(selections.sbtVersion)
    )

    val scalaVersionComboBox          = applyTo(new SComboBox())(
      setupScalaVersionItems
    )

    val step = sdkSettingsStep(settingsStep)

    val resolveClassifiersCheckBox: JCheckBox = applyTo(new JCheckBox(SbtBundle("sbt.settings.sources")))(
      _.setToolTipText("Download Scala standard library sources (useful for editing the source code)"),
      _.setSelected(selections.resolveClassifiers)
    )

    val resolveSbtClassifiersCheckBox = applyTo(new JCheckBox(SbtBundle("sbt.settings.sources")))(
      _.setToolTipText("Download sbt sources (useful for editing the project definition)"),
      _.setSelected(selections.resolveSbtClassifiers)
    )

    sbtVersionComboBox.addActionListenerEx {
      selections.sbtVersion = sbtVersionComboBox.getSelectedItem.asInstanceOf[String]
    }

    scalaVersionComboBox.addActionListenerEx {
      selections.scalaVersion = scalaVersionComboBox.getSelectedItem.asInstanceOf[String]
    }
    resolveClassifiersCheckBox.addActionListenerEx {
      selections.resolveClassifiers = resolveClassifiersCheckBox.isSelected
    }
    resolveSbtClassifiersCheckBox.addActionListenerEx {
      selections.resolveSbtClassifiers = resolveSbtClassifiersCheckBox.isSelected
    }

    val sbtVersionPanel = applyTo(new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)))(
      _.add(sbtVersionComboBox),
      _.add(resolveSbtClassifiersCheckBox)
    )

    val scalaVersionPanel = applyTo(new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)))(
      _.setBorder(new EmptyBorder(1, 0, 0, 0)),
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
    val pluginsFile = projectDir / Sbt.PluginsFile

    if (!buildFile.createNewFile() ||
            !projectDir.mkdir()) return

    (root / "src" / "main" / "scala").mkdirs()
    (root / "src" / "test" / "scala").mkdirs()

    writeToFile(buildFile, SbtModuleBuilder.formatProjectDefinition(name, platform, scalaVersion))
    writeToFile(propertiesFile, SbtModuleBuilder.formatSbtProperties(sbtVersion))
    SbtModuleBuilder.formatSbtPlugins(platform) match {
      case "" =>
      case content =>
        writeToFile(pluginsFile, content)
    }
  }

  private def sdkSettingsStep(settingsStep: SettingsStep): SdkSettingsStep = {

    val filter = new Condition[SdkTypeId] {
      def value(t: SdkTypeId): Boolean = t != null && t.isInstanceOf[JavaSdk]
    }

    new SdkSettingsStep(settingsStep, this, filter) {

      override def updateDataModel() {
        settingsStep.getContext setProjectJdk myJdkComboBox.getSelectedJdk
      }

      override def validate(): Boolean = {
        if (!super.validate()) return false

        val selectedSdk = myJdkComboBox.getSelectedJdk

        def isJava8 = JavaSdk.getInstance().getVersion(selectedSdk).isAtLeast(JavaSdkVersion.JDK_1_8)

        val scalaVersion = selections.scalaVersion
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
  }

  private def setupScalaVersionItems(cbx: SComboBox): Unit = {
    val platform = selections.scalaPlatform

    val loadedVersions = loadedScalaVersions(platform)
    cbx.setItems(loadedVersions)

    if (loadedVersions.contains(selections.scalaVersion)) {
      cbx.setSelectedItem(selections.scalaVersion)
    } else {
      if (cbx.getItemCount > 0)
        cbx.setSelectedIndex(0)
    }
  }

  private def setupDefaultVersions(): Unit = {
    if (selections.sbtVersion == null) {
      selections.sbtVersion = sbtVersions.headOption.getOrElse(Versions.DefaultSbtVersion)
    }
    if (selections.scalaVersion == null) {
      selections.scalaVersion = loadedScalaVersions(selections.scalaPlatform).headOption.getOrElse {
        selections.scalaPlatform match {
          case Dotty => Versions.DefaultDottyVersion
          case Scala => Versions.DefaultScalaVersion
        }
      }
    }
  }


  override def getNodeIcon: Icon = Sbt.Icon

  override def setupRootModel(model: ModifiableRootModel): Unit =
    tryToSetupRootModel(model, getContentEntryPath, getExternalProjectSettings)

}

private object SbtModuleBuilder {

  def formatProjectDefinition(name: String, platform: Platform, scalaVersion: String): String = platform match {
    case Scala =>
      s"""name := "$name"
         |
         |version := "0.1"
         |
         |scalaVersion := "$scalaVersion"
        """.stripMargin.trim

    case Dotty =>
      s"""
         |name := "dotty-example-project"
         |description := "Example sbt project that compiles using Dotty"
         |version := "0.1"
         |
         |scalaVersion := "$scalaVersion"
       """.stripMargin.trim
  }

  def formatSbtPlugins(platform: Platform): String = platform match {
    case Dotty => """addSbtPlugin("ch.epfl.lamp" % "sbt-dotty" % "0.1.4")"""
    case Scala => s""
  }

  def formatSbtProperties(sbtVersion: String) = s"sbt.version = $sbtVersion"
}
