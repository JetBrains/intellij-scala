package org.jetbrains.bsp.project.importing.experimental

import com.intellij.CommonBundle
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.{DialogWrapper, ValidationInfo}
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.bsp.{BspBundle, BspJdkUtil}
import org.jetbrains.bsp.project.importing.bspConfigSteps.{ConfigSetup, workspaceSetupChoices}
import org.jetbrains.bsp.project.importing.preimport.BloopPreImporter
import org.jetbrains.bsp.project.importing.setup.NoConfigSetup
import org.jetbrains.bsp.project.importing.{BspSetupConfigStep, BspSetupConfigStepUi, bspConfigSteps}
import org.jetbrains.bsp.settings.BspProjectSettings
import org.jetbrains.plugins.scala.build.IndicatorReporter
import org.jetbrains.plugins.scala.project.external.SdkUtils

import java.io.File
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
final class GenerateBspConfig(project: Project, workspace: File) {

  def runSynchronously(): Unit = {
    val configSetups: Seq[ConfigSetup] = workspaceSetupChoices(workspace)
    if (configSetups.isEmpty)
      return //TODO handle?

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

  final class GenerateBspConfigDialog(
    configSetups: Seq[ConfigSetup],
    project: Project,
    shouldShowJdkComboBox: Boolean
  ) extends DialogWrapper(project) {

    override def doValidateAll(): util.List[ValidationInfo] = {
      val validationInfo = super.doValidateAll()
      if (!configSetupUi.isJdkSelectedIfRequired()) {
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
      configSetupUi.getSelectedJdkIfRequired()

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

  //TODO: currently this handles only two cases: sbt and sbt + bloop
  //TODO: make it cancellable for both: SBT and Bloop
  //TODO: it duplicates some code with BspProjectResolver.installBSPs
  private def runConfigSetupSynchronously(setup: ConfigSetup, sdk: Sdk): Unit = {
    val parameters = bspConfigSteps.getBuilderConfigurationParameters(sdk, workspace, setup)
    parameters.bspConfigSetup match {
      case NoConfigSetup =>
        val installBloop = parameters.preImportConfig.contains(BspProjectSettings.BloopSbtPreImport)
        if (installBloop) {
          ProgressManager.getInstance.runProcessWithProgressSynchronously((() => {
            val indicator = ProgressManager.getInstance().getProgressIndicator
            val buildReporter = new IndicatorReporter(indicator)
            BloopPreImporter(workspace, sdk)(buildReporter).run()
            //NOTE: I am not sure whether this is the best name for the process
          }): Runnable, BspBundle.message("installing.bloop"), false, project)
        }
      case setup =>
        val runSetupTask = new BspSetupConfigStep.BspConfigSetupTask(setup)
        runSetupTask.queue()
    }
  }
}
