package org.jetbrains.sbt
package project
package template

import java.awt.{Color, FlowLayout, GridLayout}
import java.io.File
import com.intellij.ide.util.projectWizard.{ModuleBuilder, ModuleWizardStep, SdkSettingsStep, SettingsStep}
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalModuleBuilder
import com.intellij.openapi.module.{JavaModuleType, ModifiableModuleModel, Module, ModuleType}
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.projectRoots.{JavaSdk, JavaSdkVersion, Sdk, SdkTypeId}
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.{io, text}
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.{JBLabel, JBTextField}
import com.intellij.util.ui.UI
import com.jgoodies.forms.factories.Borders.EmptyBorder
import net.miginfocom.layout.CC
import net.miginfocom.swing.MigLayout

import javax.swing._
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaVersion}
import org.jetbrains.plugins.scala.extensions.JComponentExt.ActionListenersOwner
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.{ScalaLanguageLevel, Version, Versions}
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.project.template.SbtModuleBuilderUtil.{doSetupModule, getOrCreateContentRootDir}

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
    sbtVersion = None,
    scalaVersion = None,
    resolveClassifiers = true,
    resolveSbtClassifiers = false,
    packagePrefix = None,
  )

  private lazy val scalaVersions = ScalaKind.loadVersionsWithProgress()
  private lazy val sbtVersions = SbtKind.loadVersionsWithProgress()

  // Scala3 is only supported since sbt 1.5.0
  private val minSbtVersionForScala3 = "1.5.0"
  private lazy val sbtVersionsForScala3 = Versions(
    "1.5.2",
    sbtVersions.versions.filter(_ >= minSbtVersionForScala3)
  )

  locally {
    val settings = getExternalProjectSettings
    settings.setResolveJavadocs(false)
  }

  override def getModuleType: ModuleType[_ <: ModuleBuilder] = JavaModuleType.getModuleType

  override def createModule(moduleModel: ModifiableModuleModel): Module = {
    val root = new File(getModuleFileDirectory)
    if (root.exists()) {
      val Selections(sbtVersionOpt, scalaVersionOpt, resolveClassifiers, resolveSbtClassifiers, packagePrefix) = selections
      val sbtVersion = sbtVersionOpt.getOrElse("1.5.2")
      val scalaVersion = scalaVersionOpt.getOrElse(ScalaVersion.Latest.Scala_2_13.minor)

      locally {
        val settings = getExternalProjectSettings
        settings.setResolveClassifiers(resolveClassifiers)
        settings.setResolveSbtClassifiers(resolveSbtClassifiers)
      }

      createProjectTemplateIn(root, getName, scalaVersion, sbtVersion, packagePrefix)

      setModuleFilePath(updateModuleFilePath(getModuleFilePath))
    }

    super.createModule(moduleModel)
  }

  override def setupModule(module: Module): Unit = {
    super.setupModule(module)
    doSetupModule(module, getExternalProjectSettings, getContentEntryPath)
  }

  override def modifySettingsStep(settingsStep: SettingsStep): ModuleWizardStep = {
    {
      selections.update(ScalaKind, scalaVersions)
      selections.update(SbtKind, sbtVersions)
    }

    val sbtVersionComboBox = applyTo(new SComboBox[String]())(
      _.setItems(sbtVersions.versions.toArray),
      _.setSelectedItemSafe(selections.sbtVersion.orNull)
    )

    val scalaVersionComboBox = applyTo(new SComboBox[String]())(
      setupScalaVersionItems
    )

    val packagePrefixField = applyTo(new JBTextField())(
      _.setText(selections.packagePrefix.getOrElse("")),
      _.getEmptyText.setText(ScalaBundle.message("package.prefix.example"))
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
      selections.sbtVersion = Option(sbtVersionComboBox.getSelectedItem.asInstanceOf[String])
    }

    scalaVersionComboBox.addActionListenerEx {
      selections.scalaVersion = Option(scalaVersionComboBox.getSelectedItem.asInstanceOf[String])

      val isScala3Selected = selections.scalaVersion.exists(isScala3Version)
      val supportedSbtVersions = if (isScala3Selected) sbtVersionsForScala3 else sbtVersions
      sbtVersionComboBox.setItems(supportedSbtVersions.versions.toArray)
      // if we select Scala3 version but had Scala2 version selected before and some sbt version incompatible with Scala3,
      // the latest item from the list will be automatically selected
      sbtVersionComboBox.setSelectedItemSafe(selections.sbtVersion.orNull)
      selections.update(SbtKind, sbtVersions)
    }
    resolveClassifiersCheckBox.addActionListenerEx {
      selections.resolveClassifiers = resolveClassifiersCheckBox.isSelected
    }
    resolveSbtClassifiersCheckBox.addActionListenerEx {
      selections.resolveSbtClassifiers = resolveSbtClassifiersCheckBox.isSelected
    }
    packagePrefixField.getDocument.addDocumentListener(
      (_ => selections.packagePrefix = Option(packagePrefixField.getText).filter(_.nonEmpty)): DocumentAdapter)

    val SpaceBeforeClassifierCheckbox = 4

    val sbtVersionPanel = applyTo(new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)))(
      _.add(sbtVersionComboBox),
      _.add(Box.createHorizontalStrut(SpaceBeforeClassifierCheckbox)),
      _.add(resolveSbtClassifiersCheckBox),
    )

    val scalaVersionPanel = applyTo(new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)))(
      _.add(scalaVersionComboBox),
      _.add(Box.createHorizontalStrut(SpaceBeforeClassifierCheckbox)),
      _.add(resolveClassifiersCheckBox),
    )

    settingsStep.addSettingsField(SbtBundle.message("sbt.settings.sbt"), sbtVersionPanel)
    settingsStep.addSettingsField(SbtBundle.message("sbt.settings.scala"), scalaVersionPanel)
    settingsStep.addSettingsField(ScalaBundle.message("package.prefix.label"),
      UI.PanelFactory.panel(packagePrefixField).withTooltip(ScalaBundle.message("package.prefix.help")).createPanel())

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

  private def isScala3Version(scalaVersion: String) = scalaVersion.startsWith("3")

  private def sdkSettingsStep(settingsStep: SettingsStep) = new SdkSettingsStep(
    settingsStep,
    this,
    (_: SdkTypeId).isInstanceOf[JavaSdk]
  ) {

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

      languageLevel match {
        case _ if languageLevel >= Scala_2_12 && !JavaSdk.getInstance().getVersion(sdk).isAtLeast(JDK_1_8) =>
          reportMisconfiguration("JDK", JDK_1_8.getDescription)
        case _ =>
      }
    }
  }

  private def setupScalaVersionItems(cbx: SComboBox[String]): Unit = {
    val versions = scalaVersions.versions
    cbx.setItems(versions.toArray)

    selections.scalaVersion match {
      case Some(version) if versions.contains(version) =>
        cbx.setSelectedItemSafe(version)
      case _ if cbx.getItemCount > 0 =>
        cbx.setSelectedIndex(0)
      case _ =>
    }
  }

  override def getNodeIcon: Icon = Sbt.Icon

  override def setupRootModel(model: ModifiableRootModel): Unit = SbtModuleBuilderUtil.tryToSetupRootModel(
    model,
    getContentEntryPath,
  )

  // TODO customize the path in UI when IDEA-122951 will be implemented
  protected def updateModuleFilePath(pathname: String): String = {
    val file = new File(pathname)
    file.getParent + "/" + Sbt.ModulesDirectory + "/" + file.getName.toLowerCase
  }

}

object SbtModuleBuilder {

  import Sbt._

  private final case class Selections(var sbtVersion: Option[String],
                                      var scalaVersion: Option[String],
                                      var resolveClassifiers: Boolean,
                                      var resolveSbtClassifiers: Boolean,
                                      var packagePrefix: Option[String]) {

    import Versions.{Kind, SBT => SbtKind, Scala => ScalaKind}

    def versionFromKind(kind: Kind): Option[String] = kind match {
      case ScalaKind => scalaVersion
      case SbtKind => sbtVersion
    }

    def update(kind: Kind, versions: Versions): Unit = {
      val version = versionFromKind(kind)
        .getOrElse(kind.initiallySelectedVersion(versions.versions))

      kind match {
        case ScalaKind => scalaVersion = Some(version)
        case SbtKind   => sbtVersion   = Some(version)
      }
    }
  }

  private def createProjectTemplateIn(root: File,
                                      @NonNls name: String,
                                      @NonNls scalaVersion: String,
                                      @NonNls sbtVersion: String,
                                      packagePrefix: Option[String]): Unit = {
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
           |""".stripMargin +
          packagePrefix.map(prefix =>
            s"""
               |idePackagePrefix := Some("$prefix")
               |""".stripMargin).getOrElse("")
      )
      writeToFile(
        projectDir / PropertiesFile,
        "sbt.version = " + sbtVersion
      )
      if (packagePrefix.isDefined) {
        writeToFile(
          projectDir / PluginsFile,
          """addSbtPlugin("org.jetbrains" % "sbt-ide-settings" % "1.1.0")"""
        )
      }
    }
  }
}