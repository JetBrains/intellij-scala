package org.jetbrains.bsp.project.importing.experimental

import com.intellij.CommonBundle
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.{DialogWrapper, Messages, ValidationInfo}
import com.intellij.platform.eel.provider.EelProviderUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.bsp.project.importing.bspConfigSteps.*
import org.jetbrains.bsp.project.importing.experimental.GenerateBspConfig.{ConnectionFileSnapshot, GenerateBspConfigDialog}
import org.jetbrains.bsp.project.importing.preimport.BloopPreImporter
import org.jetbrains.bsp.project.importing.setup.NoConfigSetup
import org.jetbrains.bsp.project.importing.{BspSetupConfigStep, BspSetupConfigStepUi, bspConfigSteps}
import org.jetbrains.bsp.protocol.BspConnectionConfig
import org.jetbrains.bsp.settings.BspProjectSettings
import org.jetbrains.bsp.settings.BspProjectSettings.{AutoConfig, BspConfigFile}
import org.jetbrains.bsp.settings.PreImportConfig
import org.jetbrains.bsp.{BspBundle, BspJdkUtil, BspUtil}
import org.jetbrains.plugins.scala.build.IndicatorReporter
import org.jetbrains.plugins.scala.project.external.SdkUtils

import java.nio.file.{Files, Path}
import java.nio.file.attribute.FileTime
import java.util
import javax.swing.JComponent

/**
 * The class is needed to generate BSP configuration when a project is already opened but something is wrong with the configuration file.
 *
 * Note, that in a normal workflow bsp configuration is generated on external project linking, see
 * [[org.jetbrains.bsp.project.importing.BspSetupConfigStep]]
 *
 * It's a workaround for SCL-20865
 * (it's a workaround because I don't have much experience with BSP, so I am not sure what would the perfect solution look like)
 */
@ApiStatus.Internal
@ApiStatus.Experimental
private[bsp] final class GenerateBspConfig(project: Project, workspace: Path) {

  def runSynchronously(): Unit = {
    val configSetups: Seq[ConfigSetup] = workspaceSetupChoices(workspace)
    if (configSetups.isEmpty) {
      val possibleSetups = Seq(SbtSetup, MillSetup, ScalaCliSetup, FastpassSetup)
      val possibleSetupsText = possibleSetups.map(configChoiceName).mkString(", ")
      val message = BspBundle.message("cannot.determine.project.setup", possibleSetupsText)
      Messages.showErrorDialog(project, message, BspBundle.message("cannot.determine.project.setup.title"))
      return
    }

    val projectJdk = BspJdkUtil.findOrCreateBestJdkForProject(project)
    val (configSetupOpt, sdkOpt): (Option[ConfigSetup], Option[Sdk]) = if (configSetups.size > 1 || projectJdk.isEmpty) {
      val generateBspConfigDialog = new GenerateBspConfigDialog(configSetups, project, projectJdk.isEmpty)
      val ok = generateBspConfigDialog.showAndGet()
      if (ok) {
        val selectedConfigSetup = generateBspConfigDialog.selectedConfigSetup
        val selectedJdk = generateBspConfigDialog.getSelectedJdkIfRequired()
        selectedJdk.foreach(SdkUtils.addJdkIfNotExists)
        val sdk = projectJdk.orElse(selectedJdk)
        (Some(selectedConfigSetup), sdk)
      } else (None, None)
    } else {
      (configSetups.headOption, projectJdk)
    }
    for {
      configSetup <- configSetupOpt
      sdk <- sdkOpt
    } runConfigSetupSynchronously(configSetup, sdk)

  }

  //TODO: make it cancellable for both: SBT and Bloop
  //TODO: it duplicates some code with BspProjectResolver.installBSPs
  private def runConfigSetupSynchronously(setup: ConfigSetup, sdk: Sdk): Unit = {
    val snapshot = ConnectionFileSnapshot(workspace)

    val (bspConfigSetup, preImportConfig) = bspConfigSteps.getBspConfigurationForRegeneration(sdk, workspace, setup)
    bspConfigSetup match {
      case NoConfigSetup =>
        val installBloop = preImportConfig.contains(PreImportConfig.BloopSbtPreImport)
        if (installBloop) {
          ProgressManager.getInstance.runProcessWithProgressSynchronously((() => {
            val indicator = ProgressManager.getInstance().getProgressIndicator
            val buildReporter = new IndicatorReporter(indicator)
            BloopPreImporter(workspace, sdk)(using buildReporter, EelProviderUtil.getEelDescriptor(project)).run(indicator)
            //NOTE: I am not sure whether this is the best name for the process
          }): Runnable, BspBundle.message("installing.bloop"), false, project)
        }
      case setup =>
        val runSetupTask = new BspSetupConfigStep.BspConfigSetupTask(setup)
        runSetupTask.queue()
    }

    val changes = snapshot.complete()
    val settings = BspUtil.getBspProjectSettings(project, workspace)
    settings.foreach(GenerateBspConfig.adjustServerConfigAfterRegeneration(_, changes))
  }
}

private[bsp] object GenerateBspConfig {

  /**
   * Aligns [[BspProjectSettings.serverConfig]] with connection files after regeneration.
   *
   *  - If the generated file matches the current [[BspConfigFile]], keeps the config unchanged.
   *  - If only one connection file remains, resets to [[AutoConfig]] for automatic discovery.
   *    [[AutoConfig]] is the most flexible option, so it is preferred when only one connection file is present.
   *  - If multiple files remain, sets [[BspConfigFile]] to the file generated during regeneration.
   *
   * Without this, a stale [[BspConfigFile]] reference would cause the BSP server startup to fail (see [[BspCommunication.prepareSession]]).
   * On the other hand, with [[AutoConfig]], when multiple files are present in the `.bsp` directory, it is not predictable which file
   * will be used for the import, so the generated file is set explicitly.
   */
  def adjustServerConfigAfterRegeneration(settings: BspProjectSettings, changes: ConnectionFileChanges): Unit =
    changes.generatedFile.foreach { generatedFile =>
      settings.serverConfig match
        case BspConfigFile(path) if generatedFile == path =>
          settings.connectionFileHash = null
        case BspConfigFile(_) | AutoConfig if changes.filesAfter.size == 1 =>
          settings.serverConfig = AutoConfig
          settings.connectionFileHash = null
        case BspConfigFile(_) | AutoConfig if changes.filesAfter.size > 1 =>
          settings.serverConfig = BspConfigFile(generatedFile)
          settings.connectionFileHash = null
        case _ =>
    }

  /**
   * Snapshot of BSP connection files taken before generation.
   * Call [[complete]] after the generation step to produce a [[ConnectionFileChanges]] summary.
   *
   * @param filesBeforeModTimes files that existed before regeneration with their last modification timestamps
   */
  case class ConnectionFileSnapshot(
    workspace: Path,
    filesBeforeModTimes: Set[(Path, FileTime)]
  ) {
    /** Files that existed before regeneration. */
    val filesBefore: Set[Path] = filesBeforeModTimes.map(_._1)

    /** Produces a [[ConnectionFileChanges]] summary after regeneration. */
    def complete(): ConnectionFileChanges = {
      val filesAfter = BspConnectionConfig.workspaceConfigurationFiles(workspace).toSet
      ConnectionFileChanges(filesBeforeModTimes, filesAfter)
    }
  }

  object ConnectionFileSnapshot {
    def apply(workspace: Path): ConnectionFileSnapshot = {
      val files = BspConnectionConfig.workspaceConfigurationFiles(workspace).toSet
      val modTimes = files.map(f => (f, Files.getLastModifiedTime(f)))
      ConnectionFileSnapshot(workspace, modTimes)
    }
  }

  /**
   * Summary of the BSP connection file changes after generation.
   *
   * @param filesBeforeModTimes files that existed before regeneration with their modification times
   * @param filesAfter          files that exist after regeneration (the current state)
   */
  case class ConnectionFileChanges(
    filesBeforeModTimes: Set[(Path, FileTime)],
    filesAfter: Set[Path]
  ) {
    /** Files created during regeneration. */
    private val created: Set[Path] = filesAfter -- filesBeforeModTimes.map(_._1)

    /** Files that existed before and were overwritten during regeneration. */
    private val overwritten: Set[Path] = filesBeforeModTimes.filter { (file, modTime) =>
      Files.getLastModifiedTime(file) != modTime
    }.map(_._1)

    /** Returns the BSP connection file generated during regeneration - either an existing file that was overwritten or a newly created file. */
    val generatedFile: Option[Path] = created.headOption.orElse(overwritten.headOption)
  }

  final class GenerateBspConfigDialog(
    configSetups: Seq[ConfigSetup],
    project: Project,
    shouldShowJdkComboBox: Boolean
  ) extends DialogWrapper(project) {

    override def doValidateAll(): util.List[ValidationInfo] = {
      val validationInfo = super.doValidateAll()
      if (!configSetupUi.isJdkSelectedIfRequired) {
        validationInfo.add(new ValidationInfo(BspBundle.message("jdkComboBox.validation.tooltip")).forComponent(configSetupUi.jdkComboBox))
      }
      validationInfo
    }

    private val configSetupUi = new BspSetupConfigStepUi(
      BspBundle.message("choose.tool.to.generate.bsp.configuration"),
      configSetups,
      shouldShowJdkComboBox,
      Some(project)
    )

    def selectedConfigSetup: ConfigSetup =
      configSetupUi.selectedConfigSetup

    def getSelectedJdkIfRequired(): Option[Sdk] =
      configSetupUi.getSelectedJdkIfRequired

    locally {
      configSetupUi.updateChooseBspSetupComponent(configSetups)

      setTitle(BspBundle.message("generate.bsp.configuration"))
      setOKButtonText(CommonBundle.getOkButtonText)
      setCancelButtonText(CommonBundle.getCancelButtonText)
      init()
    }

    override def createNorthPanel(): JComponent = configSetupUi.mainComponent

    override def createCenterPanel(): JComponent = null
  }
}
