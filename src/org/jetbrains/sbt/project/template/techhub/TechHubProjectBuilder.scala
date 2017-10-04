package org.jetbrains.sbt.project.template.techhub


import java.io.{File, IOException}
import javax.swing.{Icon, JTextField}

import com.intellij.ide.projectWizard.ProjectSettingsStep
import com.intellij.ide.util.projectWizard.{ModuleBuilder, ModuleWizardStep, SdkSettingsStep, SettingsStep}
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalModuleBuilder
import com.intellij.openapi.module.{JavaModuleType, ModifiableModuleModel, Module, ModuleType}
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager}
import com.intellij.openapi.projectRoots.{JavaSdk, SdkTypeId}
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.{FileUtil, FileUtilRt}
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.io.ZipUtil
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isIdentifier
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.SbtProjectSystem
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import org.jetbrains.sbt.project.template.techhub.TechHubStarterProjects.IndexEntry

import scala.collection.JavaConverters._

/**
 * User: Dmitry.Naydanov
 * Date: 21.01.15.
 */
class TechHubProjectBuilder extends
  AbstractExternalModuleBuilder[SbtProjectSettings](SbtProjectSystem.Id, new SbtProjectSettings) with SbtRefreshCaller {

  private var allTemplates: Map[String, IndexEntry] = Map.empty

  private lazy val settingsComponents = {
    downloadTemplateList()
    new TechHubTemplateList(allTemplates.values.toArray)
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

  override def setupRootModel(modifiableRootModel: ModifiableRootModel): Unit = {
    val info = settingsComponents.getSelectedTemplate

    for {
      contentPath <- Option(getContentEntryPath)
      if contentPath.nonEmpty
      contentRootDir = new File(contentPath)
      if FileUtilRt createDirectory contentRootDir
      vContentRootDir <- Option(LocalFileSystem.getInstance refreshAndFindFileByIoFile contentRootDir)
    } {
      createStub(info, contentPath)
      modifiableRootModel.inheritSdk()
      callForRefresh(modifiableRootModel.getProject)
    }
  }

  override def getModuleType: ModuleType[_ <: ModuleBuilder] = JavaModuleType.getModuleType

  override def modifySettingsStep(settingsStep: SettingsStep): ModuleWizardStep = {
    settingsStep.addSettingsComponent(settingsComponents.getMainPanel)

    new SdkSettingsStep(settingsStep, this, (t: SdkTypeId) => t != null && t.isInstanceOf[JavaSdk]) {

      override def updateDataModel() {
        settingsStep.getContext setProjectJdk myJdkComboBox.getSelectedJdk
      }

      override def validate(): Boolean = {
        val selected = settingsComponents.getSelectedTemplate
        if (selected == null) error("Select template")

        settingsStep match {
          case projectSettingsStep: ProjectSettingsStep =>
            projectSettingsStep.getPreferredFocusedComponent match {
              case field: JTextField =>
                val txt = field.getText
              case _ =>
            }
          case _ =>
        }

        val text = settingsStep.getModuleNameField.getText
        if (!isIdentifier(text))
          error("SBT Project name must be valid Scala identifier")

        true
      }
    }
  }

  private def error(msg: String) = throw new ConfigurationException(msg, "Error")

  private def createStub(info: IndexEntry, path: String): Unit = {
    val moduleDir = new File(path)
    if (!moduleDir.exists()) moduleDir.mkdirs()

    doWithProgress(
      createTemplate(info, moduleDir, getName),
      "Downloading template..."
    )
  }

  private def downloadTemplateList(): Unit = {
    doWithProgress(
      // TODO report errors
      {allTemplates = TechHubStarterProjects.downloadIndex().get},
      "Downloading list of templates...")
  }

  private def doWithProgress(body: => Unit, title: String) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      new Runnable { override def run(): Unit = body },
      title, false, null)
  }



  private def createTemplate(template: IndexEntry, createIn: File, name: String) {
    val contentDir = FileUtil.createTempDirectory(s"${template.templateName}-template-content", "", true)
    val contentFile =  new File(contentDir, "content.zip")

    contentFile.createNewFile()

    downloadTemplate(template, contentFile, name)

    ZipUtil.extract(contentFile, contentDir,
      (dir,name) => {
        // filter out included sbt launcher stuff
        name != "sbt" &&
          name != "sbt.bat" &&
          ! dir.toPath.iterator.asScala.exists(_.getFileName == "sbt-dist")
      })

    // there should be just the one directory that contains the prepared project
    val dirs = contentDir.listFiles(f => f.isDirectory)
    assert(dirs.size == 1)
    val projectDir = dirs.head
    FileUtil.copyDirContent(projectDir, createIn)
  }

  private def downloadTemplate(entry: IndexEntry, pathTo: File, name: String, indicator: Option[ProgressIndicator] = None): Unit = {
    try {
      // hack to pass required name param when necessary. currently only name param is ever required in the templates
      val url = s"entry.downloadUrl?name=$name"
      TechHubDownloadUtil.downloadContentToFile(indicator, url, pathTo)
    } catch {
      case io: IOException =>
        error(io.getMessage)
    }
  }

}
