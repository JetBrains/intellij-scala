package org.jetbrains.plugins.cbt.project.template

import java.awt.{FlowLayout, GridLayout}
import java.io.File
import java.nio.file.{Files, Paths}
import javax.swing.event.{DocumentEvent, DocumentListener}
import javax.swing.{JPanel, _}

import com.intellij.ide.util.projectWizard.{ModuleBuilder, ModuleWizardStep, SdkSettingsStep, SettingsStep}
import com.intellij.openapi.externalSystem.service.notification.NotificationSource
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalModuleBuilder
import com.intellij.openapi.externalSystem.settings.{AbstractExternalSystemSettings, ExternalSystemSettingsListener}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.fileChooser.{FileChooserDescriptor, FileChooserDescriptorFactory}
import com.intellij.openapi.module.{JavaModuleType, ModifiableModuleModel, Module, ModuleType}
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress._
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdk, SdkTypeId}
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.ui.{ComboBox, TextFieldWithBrowseButton}
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.io.FileUtil.createDirectory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.plugins.cbt._
import org.jetbrains.plugins.cbt.process.CbtOutputListener
import org.jetbrains.plugins.cbt.project.CbtProjectSystem
import org.jetbrains.plugins.cbt.project.settings.{CbtProjectSettings, CbtSystemSettings}
import org.jetbrains.plugins.cbt.settings.CbtGlobalSettings
import org.jetbrains.plugins.scala.extensions.JComponentExt.ActionListenersOwner
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.{Platform, Versions}
import org.jetbrains.sbt.project.template.SComboBox

import scala.util.{Failure, Try}

class CbtModuleBuilder
  extends AbstractExternalModuleBuilder[CbtProjectSettings](CbtProjectSystem.Id, new CbtProjectSettings) {

  private var selections: Selections = new Selections
  private var scalaVersions: Array[String] = Array.empty

  override def setupRootModel(model: ModifiableRootModel): Unit = {
    CbtGlobalSettings.instance.lastUsedCbtExePath = selections.cbtExePath
    CbtSystemSettings.instance(model.getProject).cbtExePath = selections.cbtExePath
    for {
      contentPath <- Option(getContentEntryPath)
      contentRootDir = new File(contentPath)
      if Try(createDirectory(contentRootDir)).isSuccess
      fileSystem = LocalFileSystem.getInstance
      vContentRootDir <- Option(fileSystem.refreshAndFindFileByIoFile(contentRootDir))
    } {
      model.addContentEntry(vContentRootDir)
      model.inheritSdk()
      val settings =
        ExternalSystemApiUtil.getSettings(model.getProject, CbtProjectSystem.Id).
          asInstanceOf[AbstractExternalSystemSettings[_ <: AbstractExternalSystemSettings[_, CbtProjectSettings, _],
          CbtProjectSettings, _ <: ExternalSystemSettingsListener[CbtProjectSettings]]]

      val externalProjectSettings = getExternalProjectSettings
      externalProjectSettings.setExternalProjectPath(getContentEntryPath)
      settings.linkProject(externalProjectSettings)
      val project = model.getProject
      generateTemplate(project, contentRootDir)
    }
  }

  override def modifySettingsStep(settingsStep: SettingsStep): ModuleWizardStep = {
    setupDefaults()

    val scalaVersionComboBox =
      applyTo(new SComboBox())(
        _.setItems(loadedScalaVersions)
      )

    val cbtExePathTextField = applyTo(new TextFieldWithBrowseButton())(
      _.setText(selections.cbtExePath),
      _.addBrowseFolderListener(
        "Choose a CBT executable path",
        "Choose a CBT executable path",
        null,
        new FileChooserDescriptor(true, false, false, false, false, false))
    )

    val useCbtForInternalTasksCheckBox =
      applyTo(new JCheckBox("Use CBT for Running and Building your project"))(
        _.setSelected(selections.useCbtForInternalTasks)
      )

    val useDirectCheckBox =
      applyTo(new JCheckBox("Use CBT direct mode (use that if have some problems with nailgun)"))(
        _.setSelected(selections.useDirect)
      )

    val templateComboBox =
      applyTo(new ComboBox[CbtTemplate]) { cb =>
        val comboBoxModel = new DefaultComboBoxModel[CbtTemplate](CbtModuleBuilder.templates.toArray)
        cb.setModel(comboBoxModel)
      }


    scalaVersionComboBox.addActionListenerEx {
      selections.scalaVersion = scalaVersionComboBox.getSelectedItem.asInstanceOf[String]
    }

    useCbtForInternalTasksCheckBox.addActionListenerEx {
      selections.useCbtForInternalTasks = useCbtForInternalTasksCheckBox.isSelected
    }

    useDirectCheckBox.addActionListenerEx {
      selections.useDirect = useDirectCheckBox.isSelected
    }

    templateComboBox.addActionListenerEx {
      selections.template = templateComboBox.getSelectedItem.asInstanceOf[CbtTemplate]
    }

    cbtExePathTextField.getTextField.getDocument.addDocumentListener(new DocumentListener {
      override def removeUpdate(e: DocumentEvent): Unit = update()

      override def changedUpdate(e: DocumentEvent): Unit = update()

      override def insertUpdate(e: DocumentEvent): Unit = update()

      private def update(): Unit =
        selections.cbtExePath = cbtExePathTextField.getText
    })


    val step = sdkSettingsStep(settingsStep)

    settingsStep.addSettingsField("Scala Version:", scalaVersionComboBox)
    settingsStep.addSettingsField("Template:", templateComboBox)
    settingsStep.addSettingsField("CBT executable path:", cbtExePathTextField)
    settingsStep.addSettingsField("", useCbtForInternalTasksCheckBox)
    settingsStep.addSettingsField("", useDirectCheckBox)
    step
  }

  private def loadedScalaVersions = {
    if (scalaVersions.isEmpty)
      scalaVersions = withProgressSynchronously(s"Fetching Scala versions") { _ =>
        Versions.loadScalaVersions(Platform.Scala)
      }
    scalaVersions
  }

  private def setupDefaults(): Unit = {
    if (selections.scalaVersion == null)
      selections.scalaVersion =
        loadedScalaVersions.headOption.getOrElse(Versions.DefaultScalaVersion)
    if (selections.cbtExePath == null)
      selections.cbtExePath =
        CbtGlobalSettings.instance.lastUsedCbtExePath
  }

  private def sdkSettingsStep(settingsStep: SettingsStep) = {
    val filter = new Condition[SdkTypeId] {
      def value(t: SdkTypeId): Boolean = t != null && t.isInstanceOf[JavaSdk]
    }
    new SdkSettingsStep(settingsStep, this, filter)
  }

  override def createModule(moduleModel: ModifiableModuleModel): Module = {
    getExternalProjectSettings.isCbt = false
    getExternalProjectSettings.useCbtForInternalTasks = selections.useCbtForInternalTasks
    getExternalProjectSettings.useDirect = selections.useDirect
    super.createModule(moduleModel)
  }

  private def generateTemplate(project: Project, root: File): Unit = {
    val progressManager = ProgressManager.getInstance
    val task = runnable {
      progressManager.getProgressIndicator.setFraction(.5)
      val result = selections.template
        .generate(project, root, CbtTemplateSettings(selections.scalaVersion))
      result match {
        case Failure(e) =>
          CbtOutputListener
            .showError(project,
              s"Could not create template due to \n ${e.getMessage}",
              NotificationSource.PROJECT_SYNC)
        case _ =>
          Thread.sleep(500)// Wait to handle project resolving correctly
      }
    }
    val title = s"Applying template '${selections.template.name}'"
    //TODO find a way to use a Task instead
    progressManager
      .runProcessWithProgressAsynchronously(project, title, task, null, null, PerformInBackgroundOption.DEAF)
  }

  override def getModuleType: ModuleType[_ <: ModuleBuilder] = JavaModuleType.getModuleType

  private class Selections(var scalaVersion: String = null,
                           var useCbtForInternalTasks: Boolean = true,
                           var useDirect: Boolean = false,
                           var cbtExePath: String = null,
                           var template: CbtTemplate = CbtModuleBuilder.templates.head)
}

object CbtModuleBuilder {
  val templates: Seq[CbtTemplate] =
    Seq(
      new DefaultCbtTemplate,
      new Giter8CbtTemplate("darthorimar/cbt-seed.g8")
    )
}