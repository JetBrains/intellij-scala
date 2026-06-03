package org.jetbrains.sbt.project.sources

import com.intellij.openapi.module.{ModuleConfigurationEditor, ModuleType}
import com.intellij.openapi.roots.ui.configuration._

class SharedSourcesModuleEditorProvider extends ModuleConfigurationEditorProvider {
  override def createEditors(state: ModuleConfigurationState): Array[ModuleConfigurationEditor] = {
    val module = state.getCurrentRootModel.getModule

    ModuleType.get(module) match {
      case _: SharedSourcesModuleType => Array(
        new ContentEntriesEditor(module.getName, state),
        new ClasspathEditor(state)
      )
      case _ =>
        ModuleConfigurationEditor.EMPTY
    }
  }
}
