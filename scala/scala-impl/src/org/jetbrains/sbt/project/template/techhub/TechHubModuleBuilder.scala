package org.jetbrains.sbt.project.template.techhub


import java.io.File

import com.intellij.CommonBundle
import com.intellij.ide.util.projectWizard.{ModuleBuilder, ModuleWizardStep, SdkSettingsStep, SettingsStep}
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalModuleBuilder
import com.intellij.openapi.module.{JavaModuleType, ModifiableModuleModel, Module, ModuleType}
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.projectRoots.{JavaSdk, SdkTypeId}
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.ZipUtil
import javax.swing.Icon
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isIdentifier
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.project.template.SbtModuleBuilderUtil.tryToSetupRootModel
import org.jetbrains.sbt.{Sbt, SbtBundle}

/**
 * Creates sbt projects based on Lightbend tech hub project starter API.
 */
class TechHubModuleBuilder extends
  AbstractExternalModuleBuilder[SbtProjectSettings](SbtProjectSystem.Id, new SbtProjectSettings) with SbtRefreshCaller {

  private var allTemplates: Map[String, IndexEntry] = Map.empty

  private lazy val settingsComponents = {
    downloadTemplateList()
    new TechHubTemplateList(allTemplates.values.toArray)
  }

  override def getGroupName: String = "Scala"

  override def getBuilderId: String = "ScalaTechHubProjectBuilderId"

  override def getNodeIcon: Icon = Sbt.Icon

  override def createModule(moduleModel: ModifiableModuleModel): Module = {
    val oldPath = getModuleFilePath
    val file = new File(oldPath)
    val path = file.getParent + "/" + file.getName.toLowerCase
    setModuleFilePath(path)

    val settings = getExternalProjectSettings
    settings.setExternalProjectPath(path)
    settings.setResolveJavadocs(false)

    ModuleBuilder.deleteModuleFile(oldPath)

    val moduleType = getModuleType
    val module: Module = moduleModel.newModule(path, moduleType.getId)
    moduleModel.commit()

    setupModule(module)
    module
  }

  override def setupRootModel(model: ModifiableRootModel): Unit ={
    val info = settingsComponents.getSelectedTemplate
    for {
      contentPath <- Option(getContentEntryPath)
      moduleDir = new File(contentPath)
      if moduleDir.exists() || moduleDir.mkdirs()
    } {
      doWithProgress(
        createTemplate(info, moduleDir, getName),
        SbtBundle.message("downloading.template")
      )

      tryToSetupRootModel(model, getContentEntryPath, getExternalProjectSettings)
    }
  }

  override def getModuleType: ModuleType[_ <: ModuleBuilder] = JavaModuleType.getModuleType

  override def modifySettingsStep(settingsStep: SettingsStep): ModuleWizardStep = {
    settingsStep.addSettingsComponent(settingsComponents.getMainPanel)

    new SdkSettingsStep(settingsStep, this, (t: SdkTypeId) => t != null && t.isInstanceOf[JavaSdk]) {

      override def updateDataModel(): Unit = {
        settingsStep.getContext setProjectJdk myJdkComboBox.getSelectedJdk
      }

      override def validate(): Boolean = {
        val selected = settingsComponents.getSelectedTemplate
        if (selected == null) error(SbtBundle.message("select.template"))

        val text = settingsStep.getModuleNameLocationSettings.getModuleName
        if (!isIdentifier(text))
          error(SbtBundle.message("sbt.project.name.must.be.valid.scala.identifier"))

        true
      }
    }
  }

  private def error(@Nls msg: String) = throw new ConfigurationException(msg, CommonBundle.getErrorTitle)

  private def downloadTemplateList(): Unit =
    doWithProgress(
      // TODO report errors
      {allTemplates = TechHubStarterProjects.downloadIndex().get},
      SbtBundle.message("downloading.list.of.templates")
    )

  private def doWithProgress(body: => Unit, @Nls title: String): Unit =
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      new Runnable { override def run(): Unit = body },
      title, false, null
    )

  private def createTemplate(template: IndexEntry, createIn: File, name: String): Unit = {
    val contentDir = FileUtil.createTempDirectory(s"${template.templateName}-template-content", "", true)
    val contentFile =  new File(contentDir, "content.zip")

    contentFile.createNewFile()

    TechHubStarterProjects.downloadTemplate(template, contentFile, name, onError = error(_))

    ZipUtil.extract(contentFile, contentDir, null)

    // there should be just the one directory that contains the prepared project
    val dirs = contentDir.listFiles(f => f.isDirectory)
    assert(dirs.size == 1, "Expected only one directory in archive. Did Lightbend API change?")
    val projectDir = dirs.head
    FileUtil.copyDirContent(projectDir, createIn)
  }

}
