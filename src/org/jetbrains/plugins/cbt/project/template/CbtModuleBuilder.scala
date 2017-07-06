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

  private class Selections(var scalaVersion: String = null,
                           var isCbt: Boolean = false,
                           var useCbtForInternalTasks: Boolean = true)

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

    val useCbtFroInternalTasksCheckBox = applyTo(new JCheckBox())(
      _.setSelected(selections.useCbtForInternalTasks)
    )

    scalaVersionComboBox.addActionListenerEx {
      selections.scalaVersion = scalaVersionComboBox.getSelectedItem.asInstanceOf[String]
    }

    useCbtFroInternalTasksCheckBox.addActionListenerEx {
      selections.useCbtForInternalTasks = useCbtFroInternalTasksCheckBox.isSelected
    }

    val step = sdkSettingsStep(settingsStep)

    settingsStep.addSettingsField("Scala:", scalaVersionComboBox)
    settingsStep.addSettingsField("Use CBT for Running and Building your project:", useCbtFroInternalTasksCheckBox)
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
    getExternalProjectSettings.isCbt = selections.isCbt
    getExternalProjectSettings.useCbtForInternalTasks = selections.useCbtForInternalTasks

    if (root.exists()) {
      generateTemplate(root)
    }
    super.createModule(moduleModel)
  }

  private def generateTemplate(root: File): Unit = {
    CbtProjectGenerator(root, Version(selections.scalaVersion))
  }

  override def getModuleType: ModuleType[_ <: ModuleBuilder] = JavaModuleType.getModuleType
}
