package org.jetbrains.sbt
package project
package template

import java.awt.FlowLayout
import java.io.File

import com.intellij.ide.util.projectWizard.{ModuleBuilder, ModuleWizardStep, SdkSettingsStep, SettingsStep}
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalModuleBuilder
import com.intellij.openapi.module.{JavaModuleType, ModifiableModuleModel, Module, ModuleType}
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.projectRoots.{JavaSdk, JavaSdkVersion, Sdk, SdkTypeId}
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.{io, text}
import javax.swing._
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.extensions.JComponentExt.ActionListenersOwner
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.{ScalaLanguageLevel, Version, Versions}
import org.jetbrains.sbt.project.settings.SbtProjectSettings

/**
 * User: Dmitry Naydanov, Pavel Fatin
 * Date: 11/23/13
 */
class SbtModuleBuilder extends AbstractExternalModuleBuilder[SbtProjectSettings](
  SbtProjectSystem.Id,
  new SbtProjectSettings
) {

  import SbtModuleBuilder._
  import Versions.{SBT => SbtKind, Scala => ScalaKind}

  private val selections = Selections(
    null,
    null,
    null,
    resolveClassifiers = true,
    resolveSbtClassifiers = false
  )

  private lazy val scalaVersions = ScalaKind()
  private lazy val sbtVersions = SbtKind()

  {
    val settings = getExternalProjectSettings
    settings.setResolveJavadocs(false)
  }

  override def getModuleType: ModuleType[_ <: ModuleBuilder] = JavaModuleType.getModuleType

  override def createModule(moduleModel: ModifiableModuleModel): Module = {
    new File(getModuleFileDirectory) match {
      case root if root.exists() =>
        val Selections(sbtVersion, scalaVersion, sbtPlugins, resolveClassifiers, resolveSbtClassifiers) = selections

      {
        val settings = getExternalProjectSettings
        settings.setResolveClassifiers(resolveClassifiers)
        settings.setResolveSbtClassifiers(resolveSbtClassifiers)
      }

        createProjectTemplateIn(root, getName, scalaVersion, sbtVersion, sbtPlugins)

        setModuleFilePath(updateModuleFilePath(getModuleFilePath))
      case _ =>
    }

    super.createModule(moduleModel)
  }

  override def modifySettingsStep(settingsStep: SettingsStep): ModuleWizardStep = {
    //noinspection NameBooleanParameters
    {
      selections(ScalaKind) = scalaVersions
      selections(SbtKind) = sbtVersions
    }

    val sbtVersionComboBox = applyTo(new SComboBox())(
      _.setItems(sbtVersions.versions),
      _.setSelectedItem(selections.sbtVersion)
    )

    val scalaVersionComboBox = applyTo(new SComboBox())(
      setupScalaVersionItems
    )

    //noinspection TypeAnnotation
    val step = sdkSettingsStep(settingsStep)

    val resolveClassifiersCheckBox: JCheckBox = applyTo(new JCheckBox(SbtBundle.message("sbt.settings.sources")))(
      _.setToolTipText(SbtBundle.message("sbt.download.scala.standard.library.sources")),
      _.setSelected(selections.resolveClassifiers)
    )

    val resolveSbtClassifiersCheckBox = applyTo(new JCheckBox(SbtBundle.message("sbt.settings.sources")))(
      _.setToolTipText(SbtBundle.message("sbt.download.sbt.sources")),
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

    settingsStep.addSettingsField(SbtBundle.message("sbt.settings.sbt"), sbtVersionPanel)
    settingsStep.addSettingsField(SbtBundle.message("sbt.settings.scala"), scalaVersionPanel)

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
      settingsStep.getContext.setProjectJdk(myJdkComboBox.getSelectedJdk)
    }

    override def validate(): Boolean = super.validate() && {
      for {
        sdk <- Option(myJdkComboBox.getSelectedJdk)
        version <- Option(selections.scalaVersion)

        languageLevel <- ScalaLanguageLevel.findByVersion(version)
      } validateLanguageLevel(languageLevel, sdk)

      true
    }

    private def validateLanguageLevel(languageLevel: ScalaLanguageLevel, sdk: Sdk): Unit = {
      import JavaSdkVersion.JDK_1_8
      import Sbt.{Latest_1_0, Name}
      import ScalaLanguageLevel._

      def reportMisconfiguration(libraryName: String,
                                 libraryVersion: String) =
        throw new ConfigurationException(
          SbtBundle.message("scala.version.requires.library.version", languageLevel.getVersion, libraryName, libraryVersion),
          SbtBundle.message("wrong.library.version", libraryName)
        )

      languageLevel match {
        case Scala_3_0 if Option(selections.sbtVersion).exists(Version(_) >= Latest_1_0) =>
          selections.sbtPlugins = Scala3RequiredSbtPlugins
        case Scala_3_0 =>
          reportMisconfiguration(Name, Latest_1_0.presentation)
        case _ if languageLevel >= Scala_2_12 && !JavaSdk.getInstance().getVersion(sdk).isAtLeast(JDK_1_8) =>
          reportMisconfiguration("JDK", JDK_1_8.getDescription)
        case _ =>
      }

    }
  }

  private def setupScalaVersionItems(cbx: SComboBox): Unit = {
    val versions = scalaVersions.versions
    cbx.setItems(versions)

    selections.scalaVersion match {
      case version if versions.contains(version) =>
        cbx.setSelectedItem(version)
      case _ if cbx.getItemCount > 0 => cbx.setSelectedIndex(0)
      case _ =>
    }
  }

  override def getNodeIcon: Icon = Sbt.Icon

  override def setupRootModel(model: ModifiableRootModel): Unit = SbtModuleBuilderUtil.tryToSetupRootModel(
    model,
    getContentEntryPath,
    getExternalProjectSettings
  )

  // TODO customize the path in UI when IDEA-122951 will be implemented
  protected def updateModuleFilePath(pathname: String): String = {
    val file = new File(pathname)
    file.getParent + "/" + Sbt.ModulesDirectory + "/" + file.getName.toLowerCase
  }

}

object SbtModuleBuilder {

  import Sbt._

  @NonNls private val Scala3RequiredSbtPlugins =
    """addSbtPlugin("ch.epfl.lamp" % "sbt-dotty" % "0.3.3")
      |""".stripMargin

  private final case class Selections(var sbtVersion: String,
                                      var scalaVersion: String,
                                      var sbtPlugins: String,
                                      var resolveClassifiers: Boolean,
                                      var resolveSbtClassifiers: Boolean) {

    import Versions.{Kind, SBT => SbtKind, Scala => ScalaKind}

    def apply(kind: Kind): String = kind match {
      case ScalaKind => scalaVersion
      case SbtKind => sbtVersion
    }

    def update(kind: Kind, versions: Versions): Unit = {
      val version = apply(kind) match {
        case null =>
          val Versions(defaultVersion, versionsArray) = versions
          versionsArray.headOption.getOrElse(defaultVersion)
        case value => value
      }

      kind match {
        case ScalaKind => scalaVersion = version
        case SbtKind => sbtVersion = version
      }
    }
  }

  private def createProjectTemplateIn(root: File,
                                      @NonNls name: String,
                                      @NonNls scalaVersion: String,
                                      @NonNls sbtVersion: String,
                                      @NonNls sbtPlugins: String): Unit = {
    val buildFile = root / BuildFile
    val projectDir = root / ProjectDirectory

    if (buildFile.createNewFile() && projectDir.mkdir()) {
      (root / "src" / "main" / "scala").mkdirs()
      (root / "src" / "test" / "scala").mkdirs()

      import io.FileUtil.writeToFile

      writeToFile(
        buildFile,
        s"""name := "$name"
           |
           |version := "0.1"
           |
           |scalaVersion := "$scalaVersion"
           |""".stripMargin
      )
      writeToFile(
        projectDir / PropertiesFile,
        "sbt.version = " + sbtVersion
      )

      import text.StringUtil.isEmpty
      if (!isEmpty(sbtPlugins)) {
        writeToFile(
          projectDir / PluginsFile,
          sbtPlugins
        )
      }
    }
  }
}