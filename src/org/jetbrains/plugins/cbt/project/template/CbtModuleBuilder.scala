package org.jetbrains.plugins.cbt.project.template

import java.awt.{FlowLayout, GridLayout}
import java.io.File
import javax.swing._

import com.intellij.ide.util.projectWizard.{ModuleBuilder, ModuleWizardStep, SdkSettingsStep, SettingsStep}
import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalModuleBuilder
import com.intellij.openapi.externalSystem.settings.{AbstractExternalSystemSettings, ExternalSystemSettingsListener}
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.{JavaModuleType, ModifiableModuleModel, Module, ModuleType}
import com.intellij.openapi.projectRoots.{JavaSdk, SdkTypeId}
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.io.FileUtil.createDirectory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.plugins.cbt.project.CbtProjectSystem
import org.jetbrains.plugins.cbt.project.settings.CbtProjectSettings
import org.jetbrains.plugins.scala.extensions.JComponentExt.ActionListenersOwner
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.{Platform, Versions}
import org.jetbrains.sbt.project.template.SComboBox

class CbtModuleBuilder
  extends AbstractExternalModuleBuilder[CbtProjectSettings](CbtProjectSystem.Id, new CbtProjectSettings) {

  private val selections = new Selections()
  private var scalaVersions: Array[String] = Array.empty

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

    val scalaVersionComboBox =
      applyTo(new SComboBox())(
        _.setItems(loadedScalaVersions)
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

    val scalaPanel = applyTo(new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)))(
      _.add(scalaVersionComboBox)
    )

    val cbtSettingsPanel = applyTo(new JPanel(new GridLayout(2, 1)))(
      _.add(useCbtForInternalTasksCheckBox),
      _.add(useDirectCheckBox)
    )

    val step = sdkSettingsStep(settingsStep)

    settingsStep.addSettingsField("Scala:", scalaPanel)
    settingsStep.addSettingsField("CBT:", cbtSettingsPanel)
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
    getExternalProjectSettings.useDirect = selections.useDirect

    if (root.exists()) {
      generateTemplate(root)
    }
    super.createModule(moduleModel)
  }

  private def generateTemplate(root: File): Unit = {
    selections.template.generate(root, CbtTemplateSettings(selections.scalaVersion))
  }

  override def getModuleType: ModuleType[_ <: ModuleBuilder] = JavaModuleType.getModuleType

  private class Selections(var scalaVersion: String = null,
                           var isCbt: Boolean = false,
                           var useCbtForInternalTasks: Boolean = true,
                           var useDirect: Boolean = false,
                           var template: CbtTemplate = CbtModuleBuilder.templates.head)

}

object CbtModuleBuilder {
  val templates: Seq[CbtTemplate] =
    Seq(
      new DefaultCbtTemplate,
      new Giter8CbtTemplate("darthorimar/cbt-seed.g8")
    )
}