package org.jetbrains.sbt
package project.settings

import com.intellij.ide.projectWizard.{ProjectWizardJdkComboBox, ProjectWizardJdkIntent}
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.settings.AbstractExternalProjectSettingsControl
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil.*
import com.intellij.openapi.externalSystem.util.{ExternalSystemUtil, PaintAwarePanel}
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.messages.Topic
import com.intellij.util.ui.{GridBag, JBUI}
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.project.external.SdkUtils
import org.jetbrains.sbt.project.SbtProjectSystem

import java.awt.{BorderLayout, GridBagConstraints}
import java.nio.file.Path
import javax.swing.*
import scala.util.Try

/**
 * The settings UI is used it two places with slightly different UI:
 *  1. In `Settings | Build, Execution, Deployment | Build Tools | sbt` in `sbt Projects` subsection
 *  1. During new project creation via `Import project from existing sources` (`File | New | Project from Existing Sources...`)
 */
class SbtProjectSettingsControl(context: Context, initialSettings: SbtProjectSettings)
  extends AbstractExternalProjectSettingsControl[SbtProjectSettings](initialSettings) {

  private val jdkLabel = new JLabel(SbtBundle.message("sbt.settings.project.jdk"))
  private val jdkPanel = new JPanel(new BorderLayout())

  /**
   * The JDK combo box for the current import wizard, absent on the Settings page.
   * The import builder reuses this control, but each wizard needs a new JDK combo box.
   *
   * The [[ProjectWizardJdkComboBox]] uses a coroutine scope to manage its background JDK detection tasks.
   * Closing the wizard cancels that scope permanently. A later project path change can request detection again,
   * but the old scope cancels new tasks too. Changing the path does not create a new scope.
   * [[replaceJdkComboBox]] therefore creates a fresh combo box with a fresh scope for each wizard.
   */
  private var wizardJdkComboBox: Option[ProjectWizardJdkComboBox] = None

  /** Stores the last applied JDK choice and its SDK so repeated applies do not register duplicate SDKs. */
  private var preparedJdk: Option[(ProjectWizardJdkIntent, Sdk)] = None

  private val extraControls = new SbtExtraControls()

  override def fillExtraControls(@NotNull content: PaintAwarePanel, indentLevel: Int): Unit = {
    val labelConstraints = getLabelConstraints(indentLevel)
    val fillLineConstraints = getFillLineConstraints(indentLevel)

    val rootComponent = extraControls.rootComponent

    if (context == Context.Wizard) {
      content.add(rootComponent, fillLineConstraints)
      content.add(jdkLabel, labelConstraints)
      content.add(jdkPanel, fillLineConstraints)

      extraControls.remoteDebugSbtShellCheckBox.panel.setVisible(false)
    } else {
      // This scroll pane was introduced, because when we consider the scenario that these settings will be used
      // in "Settings | Build, Execution, Deployment | Build Tools | sbt" and the user minimizes the window as much as possible,
      // the checkbox at the bottom ("Enable debugging") is not fully visible.
      val scrollPane = new JBScrollPane(rootComponent)
      scrollPane.setBorder(null)
      content.add(scrollPane, fillLineAndColumnConstraints(indentLevel))
    }
  }

  override def isExtraSettingModified: Boolean = {
    val settings = getInitialSettings

    extraControls.resolveClassifiersCheckBox.isSelected != settings.resolveClassifiers ||
      extraControls.resolveSbtClassifiersCheckBox.isSelected != settings.resolveSbtClassifiers ||
      extraControls.useSbtShellForImportCheckBox.isSelected != settings.useSbtShellForImport ||
      extraControls.useSbtShellForBuildCheckBox.isSelected != settings.useSbtShellForBuild ||
      extraControls.remoteDebugSbtShellCheckBox.isSelected != settings.enableDebugSbtShell ||
      extraControls.scalaVersionPreferenceCheckBox.isSelected != settings.preferScala2 ||
      extraControls.useSeparateCompilerOutputPaths.isSelected != settings.useSeparateCompilerOutputPaths ||
      extraControls.generateManagedSourcesDuringProjectSync.isSelected != settings.generateManagedSourcesDuringProjectSync
  }

  /** Called once per wizard, before the wizard shows this page. The Settings page passes no `wizardContext`. */
  override protected def resetExtraSettings(isDefaultModuleCreation: Boolean, @Nullable wizardContext: WizardContext): Unit =
    if wizardContext != null then
      replaceJdkComboBox(wizardContext.getDisposable)
    super.resetExtraSettings(isDefaultModuleCreation, wizardContext)

  /**
   * Creates a fresh JDK combo box.
   * The new combo uses `wizardDisposable`, so closing this wizard stops its background tasks.
   */
  private def replaceJdkComboBox(wizardDisposable: Disposable): Unit =
    val comboBox = new ProjectWizardJdkComboBox(null, wizardDisposable, (_: Sdk) => java.lang.Boolean.TRUE, null)
    comboBox.setUsePreferredSizeAsMinimum(false)
    wizardJdkComboBox = Some(comboBox)
    jdkLabel.setLabelFor(comboBox)
    jdkPanel.removeAll()
    jdkPanel.add(comboBox, BorderLayout.CENTER)

  override protected def resetExtraSettings(isDefaultModuleCreation: Boolean): Unit = {
    val settings = getInitialSettings

    preparedJdk = None
    wizardJdkComboBox.foreach: jdkComboBox =>
      // The platform resets this reused control before supplying the new project's path.
      // The settings can therefore still contain the previous import's path and JDK name.
      // This is temporary: onLinkedProjectPathChange receives the new path next and replaces the list.
      jdkComboBox.refreshJdks(eelDescriptorOf(settings.getExternalProjectPath))
      settings.jdkName.foreach(jdkComboBox.setSelectedItem)

    extraControls.converterVersion = settings.converterVersion
    extraControls.resolveClassifiersCheckBox.setSelected(settings.resolveClassifiers)
    extraControls.resolveSbtClassifiersCheckBox.setSelected(settings.resolveSbtClassifiers)
    extraControls.useSbtShellForImportCheckBox.setSelected(settings.importWithShell)
    extraControls.useSbtShellForBuildCheckBox.setSelected(settings.buildWithShell)
    extraControls.remoteDebugSbtShellCheckBox.setSelected(settings.enableDebugSbtShell)
    extraControls.scalaVersionPreferenceCheckBox.setSelected(settings.preferScala2)
    extraControls.useSeparateCompilerOutputPaths.setSelected(settings.useSeparateCompilerOutputPaths)
    extraControls.generateManagedSourcesDuringProjectSync.setSelected(settings.generateManagedSourcesDuringProjectSync)
    extraControls.refreshCheckboxesConstraints()
  }

  override def updateInitialExtraSettings(): Unit = {
    val settings = getInitialSettings
    val shouldReload = shouldReloadProject(settings)
    applyExtraSettings(settings)
    reloadProjectIfNeeded(shouldReload, getProject)
  }

  override protected def applyExtraSettings(settings: SbtProjectSettings): Unit = {
    settings.converterVersion = extraControls.converterVersion
    settings.jdk = appliedJdkName.orNull
    settings.resolveClassifiers = extraControls.resolveClassifiersCheckBox.isSelected
    settings.resolveSbtClassifiers = extraControls.resolveSbtClassifiersCheckBox.isSelected
    settings.useSbtShellForImport = extraControls.useSbtShellForImportCheckBox.isSelected
    settings.enableDebugSbtShell = extraControls.remoteDebugSbtShellCheckBox.isSelected
    settings.preferScala2 = extraControls.scalaVersionPreferenceCheckBox.isSelected
    settings.useSeparateCompilerOutputPaths = extraControls.useSeparateCompilerOutputPaths.isSelected
    settings.generateManagedSourcesDuringProjectSync = extraControls.generateManagedSourcesDuringProjectSync.isSelected

    val shouldReload = shouldReloadProject(settings)

    val useSbtShellForBuildSettingChanged =
      settings.useSbtShellForBuild != extraControls.useSbtShellForBuildCheckBox.isSelected

    val project = getProject

    if (useSbtShellForBuildSettingChanged) {
      val newSetting = extraControls.useSbtShellForBuildCheckBox.isSelected
      settings.useSbtShellForBuild = newSetting

      if (project != null) {
        val newMode = if (newSetting) CompilerMode.SBT else CompilerMode.JPS
        project.getMessageBus.syncPublisher(SbtProjectSettingsControl.CompilerModeChangeTopic).onCompilerModeChange(newMode)
      }
    }

    reloadProjectIfNeeded(shouldReload, project)
  }

  /** Switches the JDK combo box to the machine where the linked sbt project lives. */
  def onLinkedProjectPathChange(path: String): Unit =
    wizardJdkComboBox.foreach(_.refreshJdks(eelDescriptorOf(path)))

  private def eelDescriptorOf(@Nullable path: String): EelDescriptor =
    Try(Path.of(path)).toOption
      .filter(_.isAbsolute)
      .map(_.eelDescriptor)
      .getOrElse(LocalEelDescriptor.INSTANCE)

  /** The name of the JDK to store in the settings. In the wizard this registers a detected or downloaded JDK. */
  private def appliedJdkName: Option[String] =
    wizardJdkComboBox match
      case Some(jdkComboBox) => selectedJdkIntent(jdkComboBox).flatMap(prepareJdk).map(_.getName)
      case None => getInitialSettings.jdkName

  /** Returns the selected existing JDK, detected JDK, or planned JDK download. */
  private def selectedJdkIntent(jdkComboBox: ProjectWizardJdkComboBox): Option[ProjectWizardJdkIntent] =
    Option(jdkComboBox.getSelectedItem).collect:
      case intent: ProjectWizardJdkIntent.ExistingJdk => intent
      case intent: ProjectWizardJdkIntent.DetectedJdk => intent
      case intent: ProjectWizardJdkIntent.DownloadJdk => intent

  /** Returns the registered JDK of `intent`, registering a detected or downloaded JDK on the first call. */
  private def prepareJdk(intent: ProjectWizardJdkIntent): Option[Sdk] =
    preparedJdk match
      case Some((preparedIntent, sdk)) if preparedIntent == intent => Some(sdk)
      case _ =>
        val sdk = Option(intent.prepareJdk())
        preparedJdk = sdk.map((intent, _))
        sdk

  /**
   * Removes the SDK prepared for a download after the preview throws a configuration error.
   *
   * @see [[org.jetbrains.sbt.project.SbtProjectImportBuilder#ensureProjectIsDefined]]
   */
  def discardPreparedJdkDownload(): Unit =
    preparedJdk match
      case Some((_: ProjectWizardJdkIntent.DownloadJdk, sdk)) =>
        preparedJdk = None
        SdkUtils.removeJdkIfRegistered(sdk)
      case _ =>

  private def shouldReloadProject(settings: SbtProjectSettings): Boolean =
    settings.useSeparateCompilerOutputPaths != extraControls.useSeparateCompilerOutputPaths.isSelected

  private def fillLineAndColumnConstraints(indentLevel: Int): GridBag = {
    val insets = JBUI.insets(INSETS, INSETS + INSETS * indentLevel, 0, INSETS)
    new GridBag().weightx(1).coverLine().coverColumn().fillCell().anchor(GridBagConstraints.WEST).insets(insets)
  }

  private def reloadProjectIfNeeded(shouldReload: Boolean, @Nullable project: Project): Unit =
    if (shouldReload && project != null) {
      val builder = new ImportSpecBuilder(project, SbtProjectSystem.Id).use(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
      ExternalSystemUtil.refreshProjects(builder)
    }

  override def validate(sbtProjectSettings: SbtProjectSettings): Boolean =
    wizardJdkComboBox.forall(selectedJdkIntent(_).isDefined)
}

private[jetbrains] object SbtProjectSettingsControl {
  private[jetbrains] trait CompilerModeChangeListener {
    def onCompilerModeChange(mode: CompilerMode): Unit
  }

  private[jetbrains] val CompilerModeChangeTopic: Topic[CompilerModeChangeListener] =
    new Topic("Compiler references search compiler mode change topic", classOf[CompilerModeChangeListener])
}
