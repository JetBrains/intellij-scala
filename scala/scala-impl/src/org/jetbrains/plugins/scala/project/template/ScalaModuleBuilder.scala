package org.jetbrains.plugins.scala
package project
package template

import java.{util => ju}

import com.intellij.facet.impl.ui.libraries.{LibraryCompositionSettings, LibraryOptionsPanel}
import com.intellij.framework.library.FrameworkLibraryVersionFilter
import com.intellij.ide.util.projectWizard.{JavaModuleBuilder, ModuleWizardStep, SettingsStep}
import com.intellij.openapi.module.{JavaModuleType, Module}
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.ui.configuration.projectRoot.{LibrariesContainer, LibrariesContainerFactory}
import com.intellij.openapi.util.Disposer
import javax.swing.{JComponent, JLabel}
import org.jetbrains.plugins.scala.extensions._

/**
 * @author Pavel Fatin
 */
class ScalaModuleBuilder extends JavaModuleBuilder {
  private var librariesContainer: LibrariesContainer = _

  private var libraryCompositionSettings: LibraryCompositionSettings = _

  addModuleConfigurationUpdater((_: Module, rootModel: ModifiableRootModel) => {
    val mutableEmptyList = new ju.ArrayList[Library]()
    libraryCompositionSettings.addLibraries(rootModel, mutableEmptyList, librariesContainer)
  })

  override def modifySettingsStep(settingsStep: SettingsStep): ModuleWizardStep = {
    librariesContainer = LibrariesContainerFactory.createContainer(settingsStep.getContext.getProject)

    new ScalaStep(settingsStep)
  }

  private class ScalaStep(settingsStep: SettingsStep) extends ModuleWizardStep() {
    private val javaStep = JavaModuleType.getModuleType.modifyProjectTypeStep(settingsStep, ScalaModuleBuilder.this)

    private val libraryPanel = new LibraryOptionsPanel(
      ScalaLibraryType.Description,
      "",
      FrameworkLibraryVersionFilter.ALL,
      librariesContainer,
      false
    )

    //noinspection ScalaExtractStringToBundle
    settingsStep.addSettingsField("Scala S\u001BDK:", libraryPanel.getSimplePanel)

    // TODO Remove the label patching when JavaModuleBuilder will use the proper label natively
    Option(libraryPanel.getSimplePanel.getParent).foreach { parent =>
      parent.getComponents.toSeq.foreachDefined {
        case label: JLabel if label.getText == "Project SDK:" =>
          label.setText("JDK")
          label.setDisplayedMnemonic('J')
      }
    }

    override def updateDataModel(): Unit = {
      libraryCompositionSettings = libraryPanel.apply()
      javaStep.updateDataModel()
    }

    override def getComponent: JComponent = libraryPanel.getMainPanel

    override def disposeUIResources(): Unit = {
      super.disposeUIResources()
      javaStep.disposeUIResources()
      Disposer.dispose(libraryPanel)
    }

    override def validate: Boolean = super.validate && (javaStep == null || javaStep.validate)
  }
}
