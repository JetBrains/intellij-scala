package org.jetbrains.sbt
package project.module

import com.intellij.openapi.roots.ui.configuration._
import com.intellij.openapi.module.{ModuleType, ModuleConfigurationEditor}

/**
 * User: Dmitry Naydanov
 * Date: 11/22/13
 */
class SbtModuleEditorProvider extends ModuleConfigurationEditorProvider {
  def createEditors(state: ModuleConfigurationState) = {
    val module = state.getRootModel.getModule

    if (ModuleType.get(module).isInstanceOf[SbtModuleType])
      Array(new ContentEntriesEditor(module.getName, state), new ClasspathEditor(state))
    else
      ModuleConfigurationEditor.EMPTY
  }
}
