package org.jetbrains.sbt.project.template.techhub


import com.intellij.CommonBundle
import com.intellij.ide.util.projectWizard.{ModuleBuilder, ModuleWizardStep, SettingsStep}
import com.intellij.openapi.module.{JavaModuleType, ModifiableModuleModel, Module, ModuleType}
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.ZipUtil
import org.jetbrains.annotations.{Nls, TestOnly}
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isIdentifier
import org.jetbrains.sbt.project.template.SbtModuleBuilderUtil.tryToSetupRootModel
import org.jetbrains.sbt.project.template.{SbtModuleBuilderBase, SbtModuleBuilderUtil, ScalaSettingsStepBase}
import org.jetbrains.sbt.{Sbt, SbtBundle}

import java.io.File
import javax.swing.Icon

/**
 * Creates sbt projects based on Lightbend tech hub project starter API.
 */
final class TechHubModuleBuilder extends SbtModuleBuilderBase {

  private var allTemplates: Map[String, IndexEntry] = Map.empty

  private lazy val settingsComponent = {
    downloadTemplateList()
    new TechHubTemplateList(allTemplates.values.toArray)
  }

  override def getGroupName: String = "Scala"

  override def getBuilderId: String = "ScalaTechHubProjectBuilderId"

  override def getNodeIcon: Icon = Sbt.Icon

  // TODO: shouldn't we reuse logic with from org.jetbrains.sbt.project.template.SbtModuleBuilder?
  //  Looks like the only `TechHubModuleBuilder`-specific logic is template construction:
  //  it downloads the template, and `SbtModuleBuilder` creates the template manually
  //  Otherwise the importing logic should be the same.
  override def createModule(moduleModel: ModifiableModuleModel): Module = {
    setModuleFilePath(moduleFilePathUpdated(getModuleFilePath))

    val module = moduleModel.newModule(getModuleFilePath, getModuleType.getId)
    setupModule(module) // TODO: shouldn't this method be called by platform and not by ourselves
    module
  }

  override def setupRootModel(model: ModifiableRootModel): Unit ={
    val info = settingsComponent.getSelectedTemplate
    for {
      contentPath <- Option(getContentEntryPath)
      moduleDir = new File(contentPath)
      if moduleDir.exists() || moduleDir.mkdirs()
    } {
      doWithProgress(
        downloadTemplate(info, getName, moduleDir),
        SbtBundle.message("downloading.template")
      )

      tryToSetupRootModel(model, getContentEntryPath)
    }
  }

  override def setupModule(module: Module): Unit = {
    super.setupModule(module)
    SbtModuleBuilderUtil.doSetupModule(module, getExternalProjectSettings, getContentEntryPath)
  }

  override def getModuleType: ModuleType[_ <: ModuleBuilder] = JavaModuleType.getModuleType

  override def modifySettingsStep(settingsStep: SettingsStep): ModuleWizardStep = {
    settingsStep.addSettingsComponent(settingsComponent.getMainPanel)
    new MySettingsStep(settingsStep)
  }

  final class MySettingsStep(settingsStep: SettingsStep) extends ScalaSettingsStepBase(settingsStep, this) {

    override def updateDataModel(): Unit = {
      settingsStep.getContext setProjectJdk myJdkComboBox.getSelectedJdk
    }

    override def validate(): Boolean = {
      val selected = settingsComponent.getSelectedTemplate

      // TODO: use modern validation, do not show error notification, highlight UI elements with red and add a tooltip
      if (selected == null)
        error(SbtBundle.message("select.template"))

      true
    }

    @TestOnly
    def setTemplate(templateName: String): Unit = {
      settingsComponent.setSelectedTemplateEnsuring(templateName)
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

  private def downloadTemplate(template: IndexEntry, projectName: String, downloadTo: File): Unit = {
    val contentDir = FileUtil.createTempDirectory(s"${template.templateName}-template-content", "", true)
    val contentFile =  new File(contentDir, "content.zip")

    contentFile.createNewFile()

    TechHubStarterProjects.downloadTemplate(template, contentFile, projectName, onError = error(_))

    ZipUtil.extract(contentFile.toPath, contentDir.toPath, null)

    // there should be just the one directory that contains the prepared project
    val dirs = contentDir.listFiles(f => f.isDirectory)
    assert(dirs.size == 1, "Expected only one directory in archive. Did Lightbend API change?")
    val projectDir = dirs.head
    FileUtil.copyDirContent(projectDir, downloadTo)
  }

}
