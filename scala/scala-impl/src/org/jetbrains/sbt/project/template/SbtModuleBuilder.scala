package org.jetbrains.sbt
package project
package template

import com.intellij.ide.util.projectWizard.{ModuleWizardStep, SettingsStep}
import com.intellij.openapi.module.{ModifiableModuleModel, Module}
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.projectRoots.{JavaSdk, JavaSdkVersion, Sdk}
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io
import com.intellij.util.ui.UI
import org.jetbrains.annotations.{NonNls, TestOnly}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.{ScalaLanguageLevel, Versions}
import org.jetbrains.plugins.scala.util.ui.extensions.JComboBoxOps
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaVersion}
import org.jetbrains.sbt.project.template.SbtModuleBuilderUtil.doSetupModule
import org.jetbrains.sbt.project.template.wizard.{SbtModuleStepLike, SbtModuleStepSelections}

import java.awt.{Container, FlowLayout}
import java.io.File
import javax.swing._
import scala.math.Ordering.Implicits.infixOrderingOps

/**
 * @param _selections initial selections value<br>
 *                    The parameter value is copied copied, changes to the original object do not effect the builder
 */
final class SbtModuleBuilder(
  _selections: SbtModuleStepSelections
) extends SbtModuleBuilderBase {

  private val selections = _selections.copy() // Selections is mutable data structure

  private lazy val availableScalaVersions: Versions = Versions.Scala.loadVersionsWithProgress()
  private lazy val availableSbtVersions: Versions = Versions.SBT.loadVersionsWithProgress()
  private lazy val availableSbtVersionsForScala3: Versions = Versions.SBT.sbtVersionsForScala3(availableSbtVersions)

  def this() = this(SbtModuleStepSelections.default)

  import SbtModuleBuilder._

  override def getNodeIcon: Icon = Sbt.Icon

  override def createModule(moduleModel: ModifiableModuleModel): Module = {
    val root = new File(getModuleFileDirectory)
    if (root.exists()) {
      val SbtModuleStepSelections(sbtVersionOpt, scalaVersionOpt, resolveClassifiers, resolveSbtClassifiers, packagePrefix) = selections
      val sbtVersion = sbtVersionOpt.getOrElse(Versions.SBT.LatestSbtVersion)
      val scalaVersion = scalaVersionOpt.getOrElse(ScalaVersion.Latest.Scala_2_13.minor)

      locally {
        val settings = getExternalProjectSettings
        settings.setResolveClassifiers(resolveClassifiers)
        settings.setResolveSbtClassifiers(resolveSbtClassifiers)
      }

      createProjectTemplateIn(root, getName, scalaVersion, sbtVersion, packagePrefix)

      setModuleFilePath(moduleFilePathUpdated(getModuleFilePath))
    }

    super.createModule(moduleModel)
  }

  override def setupRootModel(model: ModifiableRootModel): Unit =
    SbtModuleBuilderUtil.tryToSetupRootModel(model, getContentEntryPath)

  override def setupModule(module: Module): Unit = {
    super.setupModule(module)
    doSetupModule(module, getExternalProjectSettings, getContentEntryPath)
  }

  override def modifySettingsStep(settingsStep: SettingsStep): ModuleWizardStep =
    new SbtModuleBuilder.Step(settingsStep, selections, this)
}

object SbtModuleBuilder {

  final class Step(
    settingsStep: SettingsStep,
    override protected val selections: SbtModuleStepSelections,
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

      val packagePrefixPanel: JPanel = UI.PanelFactory
        .panel(packagePrefixField)
        .withTooltip(ScalaBundle.message("package.prefix.help"))
        .createPanel()

      settingsStep.addSettingsField(SbtBundle.message("sbt.settings.sbt"), sbtVersionPanel)
      settingsStep.addSettingsField(SbtBundle.message("sbt.settings.scala"), scalaVersionPanel)
      settingsStep.addSettingsField(ScalaBundle.message("package.prefix.label"), packagePrefixPanel)

      Option(sbtVersionPanel.getParent).foreach(patchProjectSdkLabel)
    }

    // TODO Remove the label patching when the External System will use the concise and proper labels natively
    private def patchProjectSdkLabel(parent: Container): Unit = {
      parent.getComponents.toSeq.foreachDefined {
        case label: JLabel if label.getText == "Project SDK:" =>
          label.setText("JDK:")
          label.setDisplayedMnemonic('J')

        case label: JLabel if label.getText.startsWith("Project ") && label.getText.length > 8 =>
          label.setText(label.getText.substring(8) |> (s => s.substring(0, 1).toUpperCase + s.substring(1)))
      }
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
      //  "Even when a version combination isn’t listed as supported, most features may still work.
      //   (But Scala 2.12+ definitely doesn’t work at all on JDK 6 or 7.)"
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
  }

  // TODO: mark source roots even until the project is imported
  private def createProjectTemplateIn(root: File,
                                      @NonNls name: String,
                                      @NonNls scalaVersion: String,
                                      @NonNls sbtVersion: String,
                                      packagePrefix: Option[String]): Unit = {
    val buildFile = root / Sbt.BuildFile
    val projectDir = root / Sbt.ProjectDirectory

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
           |""".stripMargin +
          packagePrefix.map(prefix =>
            s"""
               |idePackagePrefix := Some("$prefix")
               |""".stripMargin).getOrElse("")
      )
      writeToFile(
        projectDir / Sbt.PropertiesFile,
        "sbt.version = " + sbtVersion
      )
      if (packagePrefix.isDefined) {
        writeToFile(
          projectDir / Sbt.PluginsFile,
          """addSbtPlugin("org.jetbrains" % "sbt-ide-settings" % "1.1.0")"""
        )
      }
    }
  }
}