package org.jetbrains.sbt.project.template.activator

import java.io.File
import javax.swing.Icon

import com.intellij.ide.util.projectWizard.{ModuleBuilder, ModuleWizardStep, SdkSettingsStep, SettingsStep}
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalModuleBuilder
import com.intellij.openapi.externalSystem.settings.{AbstractExternalSystemSettings, ExternalSystemSettingsListener}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.{JavaModuleType, ModifiableModuleModel, Module, ModuleType}
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.projectRoots.{JavaSdk, SdkTypeId}
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.io.ZipUtil
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.project.template.activator.ActivatorRepoProcessor.DocData

/**
 * User: Dmitry.Naydanov
 * Date: 21.01.15.
 */
class ActivatorProjectBuilder extends AbstractExternalModuleBuilder[SbtProjectSettings](SbtProjectSystem.Id, new SbtProjectSettings) {
  //TODO Refactor me
  private var allTemplates: Map[String, DocData] = Map.empty
  private val repoProcessor = new ActivatorRepoProcessor
  private lazy val settingsComponents = {
    downloadTemplateList()
    new ActivatorTemplateList(allTemplates.toArray)
  }

  override def getGroupName: String = "Scala"

  override def getBuilderId: String = "ScalaActivatorProjectBuilderId"

  override def getNodeIcon: Icon = Sbt.Icon

  override def createModule(moduleModel: ModifiableModuleModel): Module = {
    val oldPath = getModuleFilePath
    val file = new File(oldPath)
    val path = file.getParent + "/" + file.getName.toLowerCase
    setModuleFilePath(path)

    val settings = getExternalProjectSettings
    settings setExternalProjectPath path
    settings setUseAutoImport true

    ModuleBuilder deleteModuleFile oldPath

    val moduleType = getModuleType
    val module: Module = moduleModel.newModule(path, moduleType.getId)
    moduleModel.commit()

    setupModule(module)
    module
  }

  override def setupRootModel(modifiableRootModel: ModifiableRootModel) {
    val selected = settingsComponents.getSelectedTemplate

    allTemplates.get(selected) map {
      case info =>
        val contentPath = getContentEntryPath
        if (StringUtil isEmpty contentPath) return

        val contentRootDir = new File(contentPath)
        FileUtilRt createDirectory contentRootDir

        val vContentRootDir = LocalFileSystem.getInstance refreshAndFindFileByIoFile contentRootDir
        if (vContentRootDir == null) return
        //todo Looks like template name can't be set without some hack (activator itself can't do it)

        createStub(info.id, contentPath)
    } getOrElse error("Can't download templates list")

    modifiableRootModel.inheritSdk()

    val settings =
      ExternalSystemApiUtil.getSettings(modifiableRootModel.getProject, SbtProjectSystem.Id).
        asInstanceOf[AbstractExternalSystemSettings[_ <: AbstractExternalSystemSettings[_, SbtProjectSettings, _],
        SbtProjectSettings, _ <: ExternalSystemSettingsListener[SbtProjectSettings]]]

    getExternalProjectSettings setExternalProjectPath getContentEntryPath
    settings linkProject getExternalProjectSettings
  }

  override def getModuleType: ModuleType[_ <: ModuleBuilder] = JavaModuleType.getModuleType

  override def modifySettingsStep(settingsStep: SettingsStep): ModuleWizardStep = {
    settingsStep.addSettingsComponent(settingsComponents.getMainPanel)

    new SdkSettingsStep(settingsStep, this, new Condition[SdkTypeId] {
      def value(t: SdkTypeId): Boolean = t != null && t.isInstanceOf[JavaSdk]
    }) {

      override def updateDataModel() {
        settingsStep.getContext setProjectJdk myJdkComboBox.getSelectedJdk
      }

      override def validate(): Boolean = {
        val context = settingsStep.getContext

        if (context.isCreatingNewProject && !ScalaNamesUtil.isIdentifier(context.getProjectName) && context.getProjectName != null)
          error("SBT Project name must be valid Scala identifier")

        val text = settingsStep.getModuleNameField.getText
        if (text == null || !ScalaNamesUtil.isIdentifier(text))
          error("SBT Project name must be valid Scala identifier")

        true
      }
    }
  }

  private def error(msg: String) = throw new ConfigurationException(msg, "Error")

  private def createStub(id: String, path: String) {
    val contentDir = FileUtilRt.createTempDirectory(s"$id-template-content", "", true)
    val contentFile =  new File(contentDir, "content.zip")

    contentFile.createNewFile()

    val moduleDir = new File(path)
    if (!moduleDir.exists()) moduleDir.mkdirs()

    doWithProgress(ActivatorRepoProcessor.downloadTemplateFromRepo(id, contentFile, error), "Downloading template...")

    ZipUtil.extract(contentFile, moduleDir, null)
  }

  private def downloadTemplateList() {
    doWithProgress({allTemplates = repoProcessor.extractRepoData()}, "Downloading list of templates...")
  }

  private def doWithProgress(body: => Unit, title: String) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable {
      override def run(): Unit = body
    }, title, false, null)
  }
}
