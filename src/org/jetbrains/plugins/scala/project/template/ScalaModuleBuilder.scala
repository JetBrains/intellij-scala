package org.jetbrains.plugins.scala
package project.template

import java.util

import com.intellij.facet.impl.ui.libraries.{LibraryCompositionSettings, LibraryOptionsPanel}
import com.intellij.framework.library.FrameworkLibraryVersionFilter
import com.intellij.ide.util.projectWizard.ModuleBuilder.ModuleConfigurationUpdater
import com.intellij.ide.util.projectWizard.{JavaModuleBuilder, ModuleWizardStep, SettingsStep}
import com.intellij.openapi.module.{JavaModuleType, Module}
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.ui.configuration.projectRoot.{LibrariesContainer, LibrariesContainerFactory}
import com.intellij.openapi.util.Disposer

/**
 * @author Pavel Fatin
 */
class ScalaModuleBuilder extends JavaModuleBuilder {
  private var librariesContainer: LibrariesContainer = _

  private var libraryCompositionSettings: LibraryCompositionSettings = _

  addModuleConfigurationUpdater(new ModuleConfigurationUpdater() {
    override def update(module: Module, rootModel: ModifiableRootModel) {
      libraryCompositionSettings.addLibraries(rootModel, new util.ArrayList[Library](), librariesContainer)
    }
  })

  override def modifySettingsStep(settingsStep: SettingsStep): ModuleWizardStep = {
    librariesContainer = LibrariesContainerFactory.createContainer(settingsStep.getContext.getProject)

    new ScalaStep(settingsStep)
  }

  private class ScalaStep(settingsStep: SettingsStep) extends ModuleWizardStep() {
    private val javaStep = JavaModuleType.getModuleType.modifyProjectTypeStep(settingsStep, ScalaModuleBuilder.this)

    private val libraryPanel = new LibraryOptionsPanel(ScalaLibraryDescription, "",
      FrameworkLibraryVersionFilter.ALL, librariesContainer, false)

    settingsStep.addSettingsField("Scala S\u001BDK:", libraryPanel.getSimplePanel)

    override def updateDataModel() {
      libraryCompositionSettings = libraryPanel.apply()
      javaStep.updateDataModel()
    }

    override def getComponent = libraryPanel.getMainPanel

    override def disposeUIResources() {
      super.disposeUIResources()
      javaStep.disposeUIResources()
      Disposer.dispose(libraryPanel)
    }

    override def validate = super.validate && (javaStep == null || javaStep.validate)
  }
}
