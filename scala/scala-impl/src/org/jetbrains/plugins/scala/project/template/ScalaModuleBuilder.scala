package org.jetbrains.plugins.scala.project.template

import com.intellij.facet.impl.ui.libraries.LibraryCompositionSettings
import com.intellij.ide.util.EditorHelper
import com.intellij.ide.util.projectWizard.{JavaModuleBuilder, ModuleWizardStep, SettingsStep}
import com.intellij.openapi.module.{JavaModuleType, Module}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.ui.configuration.projectRoot.{LibrariesContainer, LibrariesContainerFactory}
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.extensions.invokeLater

import java.{util => ju}
import javax.swing.JComponent

class ScalaModuleBuilder extends JavaModuleBuilder {

  private var _librariesContainer: LibrariesContainer = _
  def librariesContainer: LibrariesContainer = _librariesContainer

  var libraryCompositionSettings: LibraryCompositionSettings = _
  var packagePrefix = Option.empty[String]
  var openFileEditorAfterProjectOpened: Option[VirtualFile] = None

  locally {
    addModuleConfigurationUpdater((_: Module, rootModel: ModifiableRootModel) => {
      val mutableEmptyList = new ju.ArrayList[Library]()
      libraryCompositionSettings.addLibraries(rootModel, mutableEmptyList, librariesContainer)
      packagePrefix.foreach(prefix => rootModel.getContentEntries.foreach(_.getSourceFolders.foreach(_.setPackagePrefix(prefix))))
    })
  }

  override def setupModule(module: Module): Unit = {
    //execute when current dialog is closed
    invokeLater {
      openEditorForCodeSample(module.getProject)
    }
    super.setupModule(module)
  }

  override def modifySettingsStep(settingsStep: SettingsStep): ModuleWizardStep = {
    _librariesContainer = LibrariesContainerFactory.createContainer(settingsStep.getContext.getProject)

    new ScalaStep(settingsStep)
  }

  private def openEditorForCodeSample(project: Project): Unit = {
    //open code sample or buildSbt
    val fileToOpen = openFileEditorAfterProjectOpened
    fileToOpen.foreach { vFile =>
      val psiManager = PsiManager.getInstance(project)
      val psiFile = psiManager.findFile(vFile)
      if (psiFile != null) {
        EditorHelper.openInEditor(psiFile)
      }
    }
  }

  private class ScalaStep(settingsStep: SettingsStep) extends ModuleWizardStep with ScalaSDKStepLike {
    private val javaStep = JavaModuleType.getModuleType.modifyProjectTypeStep(settingsStep, ScalaModuleBuilder.this)

    locally {
      settingsStep.addSettingsField(scalaSdkLabelText, libraryPanel.getSimplePanel)
      settingsStep.addSettingsField(packagePrefixLabelText, packagePrefixPanelWithTooltip)

      Option(libraryPanel.getSimplePanel.getParent).foreach(patchProjectLabels)
    }

    override def updateDataModel(): Unit = {
      ScalaModuleBuilder.this.libraryCompositionSettings = libraryPanel.apply()
      ScalaModuleBuilder.this.packagePrefix = Option(packagePrefixTextField.getText).filter(_.nonEmpty)
      javaStep.updateDataModel()
    }

    override protected def librariesContainer: LibrariesContainer = ScalaModuleBuilder.this.librariesContainer

    override def getComponent: JComponent = libraryPanel.getMainPanel

    override def disposeUIResources(): Unit = {
      super.disposeUIResources()
      javaStep.disposeUIResources()
      Disposer.dispose(libraryPanel)
    }

    override def validate: Boolean = super.validate && (javaStep == null || javaStep.validate)
  }
}
