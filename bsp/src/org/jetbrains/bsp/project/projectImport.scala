package org.jetbrains.bsp.project

import java.io.File

import com.intellij.ide.util.newProjectWizard.AddModuleWizard
import com.intellij.ide.util.projectWizard.{ModuleWizardStep, WizardContext}
import com.intellij.ide.wizard.Step
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.wizard.{AbstractExternalProjectImportBuilder, AbstractExternalProjectImportProvider, SelectExternalProjectStep}
import com.intellij.openapi.externalSystem.service.settings.AbstractImportFromExternalSystemControl
import com.intellij.openapi.externalSystem.util.ExternalSystemSettingsControl
import com.intellij.openapi.project.{Project, ProjectManager}
import com.intellij.openapi.util.NotNullFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.{ProjectImportBuilder, ProjectOpenProcessorBase}
import javax.swing.Icon
import org.jetbrains.bsp._
import org.jetbrains.bsp.settings._

class BspProjectImportBuilder
  extends AbstractExternalProjectImportBuilder[BspImportControl](
    ProjectDataManager.getInstance(),
    BspImportControlFactory,
    BSP.ProjectSystemId) {

  override def doPrepare(context: WizardContext): Unit = {}
  override def beforeCommit(dataNode: DataNode[ProjectData], project: Project): Unit = {}
  override def getExternalProjectConfigToUse(file: File): File = file
  override def applyExtraSettings(context: WizardContext): Unit = {}
  override def getName: String = BSP.Name
  override def getIcon: Icon = BSP.Icon
}


class BspImportControl extends AbstractImportFromExternalSystemControl[BspProjectSettings, BspProjectSettingsListener, BspSettings](
  BSP.ProjectSystemId, BspSettings.getInstance(ProjectManager.getInstance.getDefaultProject), new BspProjectSettings) {

  override def onLinkedProjectPathChange(path: String): Unit = {}

  override def createProjectSettingsControl(settings: BspProjectSettings): ExternalSystemSettingsControl[BspProjectSettings] =
    new BspProjectSettingsControl(settings)

  override def createSystemSettingsControl(settings: BspSettings): ExternalSystemSettingsControl[BspSettings] =
    new BspSystemSettingsControl(settings)
}

object BspImportControlFactory extends NotNullFactory[BspImportControl] {
  override def create(): BspImportControl = new BspImportControl
}

class BspProjectImportProvider(builder: BspProjectImportBuilder)
  extends AbstractExternalProjectImportProvider(builder, BSP.ProjectSystemId) {

  def this() =
    this(ProjectImportBuilder.EXTENSIONS_POINT_NAME.findExtensionOrFail(classOf[BspProjectImportBuilder]))

  override def canImport(fileOrDirectory: VirtualFile, project: Project): Boolean =
    super.canImport(fileOrDirectory, project)

  override def createSteps(context: WizardContext): Array[ModuleWizardStep] = {
    super.createSteps(context)
  }
}

class BspProjectOpenProcessor extends ProjectOpenProcessorBase[BspProjectImportBuilder]{
  override def getSupportedExtensions: Array[String] = Array()

  override def canOpenProject(file: VirtualFile): Boolean = {
    Option(file.findChild(".bsp"))
      .exists(bspDir => bspDir.getChildren.exists(_.getExtension == "json"))
  }

  override def doGetBuilder(): BspProjectImportBuilder =
    ProjectImportBuilder.EXTENSIONS_POINT_NAME.findExtensionOrFail(classOf[BspProjectImportBuilder])

  override def doQuickImport(file: VirtualFile, wizardContext: WizardContext): Boolean = {

    val builder = getBuilder
    val provider = new BspProjectImportProvider(builder)
    val dialog = new AddModuleWizard(null, file.getPath, provider)

    builder.prepare(wizardContext)
    builder.getControl(null).setLinkedProjectPath(file.getPath)

    dialog.getWizardContext.setProjectBuilder(builder)
    dialog.navigateToStep((step: Step) => step.isInstanceOf[SelectExternalProjectStep])

    if (StringUtil.isEmpty(wizardContext.getProjectName)) {
      val projectName = dialog.getWizardContext.getProjectName
      if (!StringUtil.isEmpty(projectName)) {
        wizardContext.setProjectName(projectName)
      }
    }

    dialog.doFinishAction()
    true
  }

}