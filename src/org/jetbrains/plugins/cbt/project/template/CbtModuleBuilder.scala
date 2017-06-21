package org.jetbrains.plugins.cbt.project.template

import java.io.File
import javax.swing.JCheckBox

import com.intellij.ide.util.projectWizard.{ModuleBuilder, ModuleWizardStep, SdkSettingsStep, SettingsStep}
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalModuleBuilder
import com.intellij.openapi.externalSystem.settings.{AbstractExternalSystemSettings, ExternalSystemSettingsListener}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.{JavaModuleType, ModifiableModuleModel, Module, ModuleType}
import com.intellij.openapi.projectRoots.{JavaSdk, SdkTypeId}
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.io.FileUtil.createDirectory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.plugins.cbt.CBT
import org.jetbrains.plugins.cbt.project.CbtProjectSystem
import org.jetbrains.plugins.cbt.project.settings.CbtProjectSettings
import org.jetbrains.plugins.scala.extensions.JComponentExt.ActionListenersOwner
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.{Platform, Version, Versions}
import org.jetbrains.sbt.{RichFile, Sbt, SbtBundle}
import org.jetbrains.sbt.project.template.SComboBox


class CbtModuleBuilder
  extends AbstractExternalModuleBuilder[CbtProjectSettings](CbtProjectSystem.Id, new CbtProjectSettings) {

  private class Selections(var scalaVersion: String = null, var linkCbtLibs: Boolean = true)

  private val selections = new Selections()

  private var scalaVersions: Array[String] = Array.empty

  private def loadedScalaVersions = {
    if (scalaVersions.isEmpty)
      scalaVersions = withProgressSynchronously(s"Fetching Scala versions") { _ =>
        Versions.loadScalaVersions(Platform.Scala)
      }
    scalaVersions
  }

  private def setupDefaults() = {
    if (selections.scalaVersion == null)
      selections.scalaVersion =
        loadedScalaVersions.headOption.getOrElse(Versions.DefaultScalaVersion)
  }

  override def setupRootModel(model: ModifiableRootModel): Unit = {
    //    val contentPath = getContentEntryPath
    //    if (StringUtil.isEmpty(contentPath)) return
    //
    //    val contentRootDir = new File(contentPath)
    //    createDirectory(contentRootDir)
    //
    //    val fileSystem = LocalFileSystem.getInstance
    //    val vContentRootDir = fileSystem.refreshAndFindFileByIoFile(contentRootDir)
    //    if (vContentRootDir == null) return
    //
    //    model.addContentEntry(vContentRootDir)
    //    model.inheritSdk()
    //
    //
    //    FileDocumentManager.getInstance.saveAllDocuments()
    //    ApplicationManager.getApplication.invokeLater(new Runnable() {
    //      override def run(): Unit =
    //        ExternalSystemUtil.refreshProjects(
    //          new ImportSpecBuilder(model.getProject, CbtProjectSystem.Id)
    //            .forceWhenUptodate()
    //            .use(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
    //        )
    //    })
    val contentPath = getContentEntryPath
    if (StringUtil.isEmpty(contentPath)) return

    val contentRootDir = new File(contentPath)
    createDirectory(contentRootDir)

    val fileSystem = LocalFileSystem.getInstance
    val vContentRootDir = fileSystem.refreshAndFindFileByIoFile(contentRootDir)
    if (vContentRootDir == null) return

    model.addContentEntry(vContentRootDir)
    model.inheritSdk()
    val settings =
      ExternalSystemApiUtil.getSettings(model.getProject, CbtProjectSystem.Id).
        asInstanceOf[AbstractExternalSystemSettings[_ <: AbstractExternalSystemSettings[_, CbtProjectSettings, _],
        CbtProjectSettings, _ <: ExternalSystemSettingsListener[CbtProjectSettings]]]

    val externalProjectSettings = getExternalProjectSettings
    externalProjectSettings.setExternalProjectPath(getContentEntryPath)
    settings.linkProject(externalProjectSettings)

  }

  override def modifySettingsStep(settingsStep: SettingsStep): ModuleWizardStep = {
    setupDefaults()

    val scalaVersionComboBox = applyTo(new SComboBox())(
      _.setItems(loadedScalaVersions)
    )

    scalaVersionComboBox.addActionListenerEx {
      selections.scalaVersion = scalaVersionComboBox.getSelectedItem.asInstanceOf[String]
    }

    val linkCbtLibsCheckBox: JCheckBox = applyTo(new JCheckBox("Link CBT libraries"))(
      _.setToolTipText("Link CBT kibraries to every moudle in the project (should be disabled if editing CBT's source code)"),
      _.setSelected(selections.linkCbtLibs)
    )

    linkCbtLibsCheckBox.addActionListenerEx {
      selections.linkCbtLibs = linkCbtLibsCheckBox.isSelected
    }
    val step = sdkSettingsStep(settingsStep)

    settingsStep.addSettingsField("Scala:", scalaVersionComboBox)
    settingsStep.addSettingsField("CBT:", linkCbtLibsCheckBox)
    step
  }

  private def sdkSettingsStep(settingsStep: SettingsStep) = {
    val filter = new Condition[SdkTypeId] {
      def value(t: SdkTypeId): Boolean = t != null && t.isInstanceOf[JavaSdk]
    }
    new SdkSettingsStep(settingsStep, this, filter)
  }

  override def createModule(moduleModel: ModifiableModuleModel): Module = {
    val root = new File(getModuleFileDirectory)
    getExternalProjectSettings.linkCbtLibs = selections.linkCbtLibs

    if (root.exists()) {
      updateModulePath()
      generateTemplate(root)
    }
    super.createModule(moduleModel)
  }

  private def updateModulePath() {
    val file = new File(getModuleFilePath)
    val path = file.getParent + "/" + Sbt.ModulesDirectory + "/" + file.getName.toLowerCase
    setModuleFilePath(path)
  }

  private def generateTemplate(root: File): Unit = {
    CbtProjectGenerator(root, Version(selections.scalaVersion))
  }

  override def getModuleType: ModuleType[_ <: ModuleBuilder] = JavaModuleType.getModuleType
}
