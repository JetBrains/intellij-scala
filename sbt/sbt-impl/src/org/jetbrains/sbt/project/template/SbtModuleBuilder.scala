package org.jetbrains.sbt.project.template

import com.intellij.ide.util.projectWizard.{ModuleWizardStep, SettingsStep}
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.projectRoots.{JavaSdk, JavaSdkVersion, Sdk}
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.{ApiStatus, NonNls, TestOnly}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.template.patchProjectLabels
import org.jetbrains.plugins.scala.project.{ScalaLanguageLevel, Version, Versions}
import org.jetbrains.plugins.scala.util.ui.extensions.JComboBoxOps
import org.jetbrains.sbt.project.template.wizard.SbtModuleStepLike
import org.jetbrains.sbt.{Sbt, SbtBundle}

import java.awt.FlowLayout
import java.io.File
import javax.swing._
import scala.math.Ordering.Implicits.infixOrderingOps

/**
 * Do not extend, it will be made final in the future.<br>
 * Consider using [[SbtModuleBuilderBase]] instead
 *
 * @param _selections initial selections value<br>
 *                    The parameter value is copied copied, changes to the original object do not effect the builder
 */
@ApiStatus.Internal
class SbtModuleBuilder(
  _selections: SbtModuleBuilderSelections
) extends SbtModuleBuilderBase {

  private val selections = _selections.copy() // Selections is mutable data structure

  private lazy val availableScalaVersions: Versions = Versions.Scala.loadVersionsWithProgress()
  private lazy val availableSbtVersions: Versions = Versions.SBT.loadVersionsWithProgress()
  private lazy val availableSbtVersionsForScala3: Versions = Versions.SBT.sbtVersionsForScala3(availableSbtVersions)

  def this() = this(SbtModuleBuilderSelections.default)

  override def getNodeIcon: Icon = Sbt.Icon

  override def setupModule(module: Module): Unit = {
    val settings = getExternalProjectSettings
    settings.setResolveClassifiers(selections.downloadScalaSdkSources)
    settings.setResolveSbtClassifiers(selections.downloadSbtSources)

    super.setupModule(module)
  }

  override protected def createProjectTemplateIn(root: File): Option[DefaultModuleContentEntryFolders] = {
    val name = getName
    val sbtVersion = selections.sbtVersion.getOrElse(Versions.SBT.LatestSbtVersion)
    val scalaVersion = selections.scalaVersion.getOrElse(ScalaVersion.Latest.Scala_2_13.minor)
    val packagePrefix = selections.packagePrefix

    SbtModuleBuilder.createProjectTemplateIn(root, name, scalaVersion, sbtVersion, packagePrefix)
  }

  override def modifySettingsStep(settingsStep: SettingsStep): ModuleWizardStep =
    new SbtModuleBuilder.Step(settingsStep, selections, this)
}

object SbtModuleBuilder {

  final class Step(
    settingsStep: SettingsStep,
    override protected val selections: SbtModuleBuilderSelections,
    sbtModuleBuilder: SbtModuleBuilder
  ) extends ScalaSettingsStepBase(settingsStep, sbtModuleBuilder)
    with SbtModuleStepLike {

    // NOTE: ModuleWizardStep is recreated on validation failures, so to avoid multiple "Scala / Sbt versions download"
    // after each validation failure, we need to take this lazy values from the sbtModuleBuilder, which is not recreated
    override protected val availableScalaVersions: Versions = sbtModuleBuilder.availableScalaVersions
    override protected val availableSbtVersions: Versions = sbtModuleBuilder.availableSbtVersions
    override protected val availableSbtVersionsForScala3: Versions = sbtModuleBuilder.availableSbtVersionsForScala3

    locally {
      initSelectionsAndUi()

      //
      // Add UI elements to the Wizard Step
      //
      val SpaceBeforeClassifierCheckbox = 4

      val sbtVersionPanel = applyTo(new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)))(
        _.add(sbtVersionComboBox),
        _.add(Box.createHorizontalStrut(SpaceBeforeClassifierCheckbox)),
        _.add(downloadSbtSourcesCheckbox),
      )

      val scalaVersionPanel = applyTo(new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)))(
        _.add(scalaVersionComboBox),
        _.add(Box.createHorizontalStrut(SpaceBeforeClassifierCheckbox)),
        _.add(downloadScalaSourcesCheckbox),
      )

      settingsStep.addSettingsField(sbtLabelText, sbtVersionPanel)
      settingsStep.addSettingsField(scalaLabelText, scalaVersionPanel)
      settingsStep.addSettingsField(packagePrefixLabelText, packagePrefixPanelWithTooltip)

      Option(sbtVersionPanel.getParent).foreach(patchProjectLabels)
    }

    override def updateDataModel(): Unit = {
      settingsStep.getContext.setProjectJdk(myJdkComboBox.getSelectedJdk)
    }

    @throws[ConfigurationException]
    override def validate(): Boolean = super.validate() && {
      for {
        sdk <- Option(myJdkComboBox.getSelectedJdk)
        version <- selections.scalaVersion

        languageLevel <- ScalaLanguageLevel.findByVersion(version)
      } validateLanguageLevel(languageLevel, sdk)

      true
    }

    @throws[ConfigurationException]
    private def validateLanguageLevel(languageLevel: ScalaLanguageLevel, sdk: Sdk): Unit = {
      import JavaSdkVersion.JDK_1_8
      import ScalaLanguageLevel._

      def reportMisconfiguration(libraryName: String,
                                 libraryVersion: String) =
        throw new ConfigurationException(
          SbtBundle.message("scala.version.requires.library.version", languageLevel.getVersion, libraryName, libraryVersion),
          SbtBundle.message("wrong.library.version", libraryName)
        )

      //https://docs.scala-lang.org/overviews/jdk-compatibility/overview.html
      // TODO (minor) carefully update for other JDK versions, but maybe show a warning instead of error, cause the site states:
      //  "Even when a version combination isn't listed as supported, most features may still work.
      //   (But Scala 2.12+ definitely doesn't work at all on JDK 6 or 7.)"
      //
      val jdk = JavaSdk.getInstance().getVersion(sdk)
      if (jdk < JDK_1_8 && languageLevel >= Scala_2_12) {
        reportMisconfiguration("JDK", JDK_1_8.getDescription)
      }
    }

    @TestOnly
    def setScalaVersion(version: String): Unit = {
      scalaVersionComboBox.setSelectedItemEnsuring(version)
    }

    @TestOnly
    def setSbtVersion(version: String): Unit = {
      sbtVersionComboBox.setSelectedItemEnsuring(version)
    }

    @TestOnly
    def setPackagePrefix(prefix: String): Unit = {
      packagePrefixTextField.setText(prefix)
    }
  }

  private def createProjectTemplateIn(
    root: File,
    @NonNls name: String,
    @NonNls scalaVersion: String,
    @NonNls sbtVersion: String,
    packagePrefix: Option[String]
  ): Option[DefaultModuleContentEntryFolders] = {
    val buildFile = root / Sbt.BuildFile
    val projectDir = root / Sbt.ProjectDirectory

    if (buildFile.createNewFile() && projectDir.mkdir()) {
      val mainSourcesPath = "src/main/scala"
      val testSourcesPath = "src/test/scala"

      (root / mainSourcesPath).mkdirs()
      (root / testSourcesPath).mkdirs()

      val rootProjectSettings: Seq[String] = Seq(
        s"""name := "$name""""
      ) ++ packagePrefix.map { p =>
        s"""idePackagePrefix := Some("$p")""".stripMargin
      }

      val version = """0.1.0-SNAPSHOT"""

      // Slash syntax was introduced in sbt 1.1 (https://www.scala-sbt.org/1.x/docs/Migrating-from-sbt-013x.html)
      val isAtLeastSbt_1_1 = Version(sbtVersion) >= Version("1.1")
      val buildSbtBaseContent = if (isAtLeastSbt_1_1)
        s"""ThisBuild / version := "$version"
           |
           |ThisBuild / scalaVersion := "$scalaVersion""""
      else
        s"""version in ThisBuild := "$version"
           |
           |scalaVersion in ThisBuild := "$scalaVersion""""

      val indent = "    "
      val buildSbtContent =
        s"""$buildSbtBaseContent
           |
           |lazy val root = (project in file("."))
           |  .settings(
           |$indent${rootProjectSettings.mkString("", s",\n$indent", "")}
           |  )
           |""".stripMargin

      val buildPropertiesContent = s"""sbt.version = $sbtVersion"""

      val pluginsSbtContent = """addSbtPlugin("org.jetbrains.scala" % "sbt-ide-settings" % "1.1.2")"""

      def ensureSingleNewLineAfter(text: String): String = text.stripTrailing() + "\n"

      FileUtil.writeToFile(buildFile, ensureSingleNewLineAfter(buildSbtContent))
      FileUtil.writeToFile(projectDir / Sbt.PropertiesFile, ensureSingleNewLineAfter(buildPropertiesContent))

      if (packagePrefix.isDefined) {
        FileUtil.writeToFile(projectDir / Sbt.PluginsFile, ensureSingleNewLineAfter(pluginsSbtContent))
      }

      Some(DefaultModuleContentEntryFolders(
        Seq(mainSourcesPath),
        Seq(testSourcesPath),
        Nil,
        Nil,
      ))
    }
    else None
  }
}