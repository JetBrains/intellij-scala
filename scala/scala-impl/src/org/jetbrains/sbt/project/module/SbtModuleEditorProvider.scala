package org.jetbrains.sbt
package project.module

import com.intellij.openapi.module.{ModuleConfigurationEditor, ModuleType}
import com.intellij.openapi.roots.ui.configuration._

class SbtModuleEditorProvider extends ModuleConfigurationEditorProvider {
  override def createEditors(state: ModuleConfigurationState): Array[ModuleConfigurationEditor] = {
    val module = state.getCurrentRootModel.getModule

    ModuleType.get(module) match {
      case _: SbtModuleType => Array(
        new ContentEntriesEditor(module.getName, state),
        new DefaultModuleConfigurationEditorFactoryImpl().createOutputEditor(state),
        new ClasspathEditor(state),
        new SbtModuleSettingsEditor(state)
      )
      case _ =>
        ModuleConfigurationEditor.EMPTY
    }
  }
}
