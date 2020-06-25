package org.jetbrains.sbt.project.sources

import com.intellij.openapi.module.{ModuleConfigurationEditor, ModuleType}
import com.intellij.openapi.roots.ui.configuration._

/**
 * @author Pavel Fatin
 */
class SharedSourcesModuleEditorProvider extends ModuleConfigurationEditorProvider {
  override def createEditors(state: ModuleConfigurationState): Array[ModuleConfigurationEditor] = {
    val module = state.getRootModel.getModule

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
