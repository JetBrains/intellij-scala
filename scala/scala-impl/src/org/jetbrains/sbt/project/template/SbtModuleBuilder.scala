package org.jetbrains.sbt
package project
package template

import java.awt.FlowLayout
import java.io.File

import com.intellij.ide.util.projectWizard.{ModuleBuilder, ModuleWizardStep, SdkSettingsStep, SettingsStep}
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalModuleBuilder
import com.intellij.openapi.module.{JavaModuleType, ModifiableModuleModel, Module, ModuleType}
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.projectRoots.{JavaSdk, JavaSdkVersion, SdkTypeId}
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import javax.swing._
import org.jetbrains.plugins.scala.extensions.JComponentExt.ActionListenersOwner
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.{Version, Versions}
import org.jetbrains.sbt.project.settings.SbtProjectSettings

/**
 * User: Dmitry Naydanov, Pavel Fatin
 * Date: 11/23/13
 */
final class SbtModuleBuilder extends AbstractExternalModuleBuilder[SbtProjectSettings](
  SbtProjectSystem.Id,
  new SbtProjectSettings
) {

  import SbtModuleBuilder._
  import Versions._

  private val selections = Selections(
    null,
    null,
    resolveClassifiers = true,
    resolveSbtClassifiers = false
  )

  private lazy val (scalaVersions, defaultScalaVersion) = loadScalaVersions()
  private lazy val (sbtVersions, defaultSbtVersion) = loadSbtVersions()

  {
    val settings = getExternalProjectSettings
    settings.setResolveJavadocs(false)
    settings.setUseAutoImport(false)
    settings.setCreateEmptyContentRootDirectories(false)
  }

  def getModuleType: ModuleType[_ <: ModuleBuilder] = JavaModuleType.getModuleType

  override def createModule(moduleModel: ModifiableModuleModel): Module = {
    new File(getModuleFileDirectory) match {
      case root if root.exists() =>
        val Selections(sbtVersion, scalaVersion, resolveClassifiers, resolveSbtClassifiers) = selections

      {
        val settings = getExternalProjectSettings
        settings.setResolveClassifiers(resolveClassifiers)
        settings.setResolveSbtClassifiers(resolveSbtClassifiers)
      }

        createProjectTemplateIn(root, getName, scalaVersion, sbtVersion)

        setModuleFilePath(updateModuleFilePath(getModuleFilePath))
      case _ =>
    }

    super.createModule(moduleModel)
  }

  override def modifySettingsStep(settingsStep: SettingsStep): ModuleWizardStep = {
    setupDefaultVersions()

    val sbtVersionComboBox = applyTo(new SComboBox())(
      _.setItems(sbtVersions),
      _.setSelectedItem(selections.sbtVersion)
    )

    val scalaVersionComboBox = applyTo(new SComboBox())(
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
      _.setBorder(new border.EmptyBorder(1, 0, 0, 0)),
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

  private def sdkSettingsStep(settingsStep: SettingsStep) = new SdkSettingsStep(
    settingsStep,
    this,
    (_: SdkTypeId).isInstanceOf[JavaSdk]
  ) {

    override def updateDataModel(): Unit = {
      settingsStep.getContext.setProjectJdk(selectedJdk)
    }

    override def validate(): Boolean = super.validate() && {
      selectedJdk match {
        case null => true
        case sdk =>
          selections.scalaVersion match {
            case null => true
            case presentation =>
              if (Version(presentation) < Version("2.12") ||
                JavaSdk.getInstance().getVersion(sdk).isAtLeast(JavaSdkVersion.JDK_1_8)) true
              else throw new ConfigurationException("Scala 2.12 requires JDK 1.8", "Wrong JDK version")
          }
      }
    }

    private def selectedJdk = myJdkComboBox.getSelectedJdk
  }

  private def setupScalaVersionItems(cbx: SComboBox): Unit = {
    cbx.setItems(scalaVersions)

    selections.scalaVersion match {
      case version if scalaVersions.contains(version) =>
        cbx.setSelectedItem(version)
      case _ if cbx.getItemCount > 0 => cbx.setSelectedIndex(0)
      case _ =>
    }
  }

  private def setupDefaultVersions(): Unit = {
    selections.sbtVersion match {
      case null =>
      case _ =>
        selections.sbtVersion = sbtVersions.headOption.getOrElse(defaultSbtVersion)
    }

    selections.scalaVersion match {
      case null =>
      case _ =>
        selections.scalaVersion = scalaVersions.headOption.getOrElse(defaultScalaVersion)
    }
  }


  override def getNodeIcon: Icon = Sbt.Icon

  override def setupRootModel(model: ModifiableRootModel): Unit = SbtModuleBuilderUtil.tryToSetupRootModel(
    model,
    getContentEntryPath,
    getExternalProjectSettings
  )

}

object SbtModuleBuilder {

  private case class Selections(var sbtVersion: String,
                                var scalaVersion: String,
                                var resolveClassifiers: Boolean,
                                var resolveSbtClassifiers: Boolean)

  private def createProjectTemplateIn(root: File,
                                      name: String,
                                      scalaVersion: String,
                                      sbtVersion: String): Unit = {
    val buildFile = root / Sbt.BuildFile
    val projectDir = root / Sbt.ProjectDirectory
    if (buildFile.createNewFile() && projectDir.mkdir()) {
      (root / "src" / "main" / "scala").mkdirs()
      (root / "src" / "test" / "scala").mkdirs()

      import FileUtil.writeToFile

      writeToFile(
        buildFile,
        s"""name := "$name"
           |
           |version := "0.1"
           |
           |scalaVersion := "$scalaVersion"
          """.stripMargin.trim
      )
      writeToFile(
        projectDir / Sbt.PropertiesFile,
        "sbt.version = " + sbtVersion
      )
    }
  }

  // TODO customize the path in UI when IDEA-122951 will be implemented
  private def updateModuleFilePath(pathname: String) = {
    val file = new File(pathname)
    file.getParent + "/" + Sbt.ModulesDirectory + "/" + file.getName.toLowerCase
  }
}