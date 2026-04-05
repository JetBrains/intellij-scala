package org.jetbrains.bsp.project.importing.experimental

import com.intellij.CommonBundle
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.{DialogWrapper, Messages, ValidationInfo}
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.bsp.project.importing.bspConfigSteps._
import org.jetbrains.bsp.project.importing.experimental.GenerateBspConfig.GenerateBspConfigDialog
import org.jetbrains.bsp.project.importing.preimport.BloopPreImporter
import org.jetbrains.bsp.project.importing.setup.NoConfigSetup
import org.jetbrains.bsp.project.importing.{BspSetupConfigStep, BspSetupConfigStepUi, bspConfigSteps}
import org.jetbrains.bsp.protocol.BspConnectionConfig
import org.jetbrains.bsp.settings.{BspProjectSettings, PreImportConfig}
import org.jetbrains.bsp.{BspBundle, BspJdkUtil, BspUtil}
import org.jetbrains.plugins.scala.build.IndicatorReporter
import org.jetbrains.plugins.scala.project.external.SdkUtils

import java.nio.file.{Files, Path}
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
final class GenerateBspConfig(project: Project, workspace: Path) {

  def runSynchronously(): Unit = {
    val configSetups: Seq[ConfigSetup] = workspaceSetupChoices(workspace)
    if (configSetups.isEmpty) {
      val possibleSetups = Seq(SbtSetup, MillSetup, ScalaCliSetup, FastpassSetup)
      val possibleSetupsText = possibleSetups.map(configChoiceName).mkString(", ")
      val message = BspBundle.message("cannot.determine.project.setup", possibleSetupsText)
      Messages.showErrorDialog(project, message, BspBundle.message("cannot.determine.project.setup.title"))
      return
    }

    val projectJdk = BspJdkUtil.findOrCreateBestJdkForProject(Some(project))
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
    val filesBefore = BspConnectionConfig.workspaceConfigurationFiles(workspace).toSet

    val parameters = bspConfigSteps.getBuilderConfigurationParameters(sdk, workspace, setup)
    parameters.bspConfigSetup match {
      case NoConfigSetup =>
        val installBloop = parameters.preImportConfig.contains(PreImportConfig.BloopSbtPreImport)
        if (installBloop) {
          ProgressManager.getInstance.runProcessWithProgressSynchronously((() => {
            val indicator = ProgressManager.getInstance().getProgressIndicator
            val buildReporter = new IndicatorReporter(indicator)
            BloopPreImporter(workspace, sdk)(buildReporter).run(indicator)
            //NOTE: I am not sure whether this is the best name for the process
          }): Runnable, BspBundle.message("installing.bloop"), false, project)
        }
      case setup =>
        val runSetupTask = new BspSetupConfigStep.BspConfigSetupTask(setup)
        runSetupTask.queue()
    }

    updateStaleServerConfig(filesBefore)
  }

  /**
   * When [[BspProjectSettings.serverConfig]] is a [[BspProjectSettings.BspConfigFile]] whose path no longer exists
   * and the generation produced a new, differently named connection file, resets the config to
   * [[BspProjectSettings.AutoConfig]] so that the newly generated file can be discovered on BSP server startup.
   *
   * Without this, a stale [[BspProjectSettings.BspConfigFile]] reference would cause the BSP server startup to fail
   * (see [[BspCommunication.prepareSession]]).
   */
  private def updateStaleServerConfig(filesBefore: Set[Path]): Unit =
    for {
      settings <- BspUtil.getBspProjectSettings(project, workspace)
      serverConfig = settings.serverConfig
      BspProjectSettings.BspConfigFile(oldPath) <- Some(serverConfig)
      if !Files.exists(oldPath)
    } {
      val filesAfter = BspConnectionConfig.workspaceConfigurationFiles(workspace).toSet
      val createdFiles = filesAfter -- filesBefore
      if (createdFiles.nonEmpty) {
        settings.setServerConfig(BspProjectSettings.AutoConfig)
        settings.setConnectionFileHash(null) // set this to null, to not regenerate connection file on the subsequent BSP server startup
      }
    }
}

private[bsp] object GenerateBspConfig {

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
      shouldShowJdkComboBox
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
