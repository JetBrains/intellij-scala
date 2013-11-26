package org.jetbrains.sbt
package settings.moduleConfig

import com.intellij.openapi.roots.ui.configuration._
import com.intellij.openapi.module.{ModuleType, Module, ModuleConfigurationEditor}
import com.intellij.openapi.roots.ModifiableRootModel
import org.jetbrains.sbt.project.SbtModuleType
import java.util

/**
 * User: Dmitry Naydanov
 * Date: 11/22/13
 */
class SbtModuleEditorProvider extends ModuleConfigurationEditorProvider {
  def createEditors(state: ModuleConfigurationState): Array[ModuleConfigurationEditor] = {
    val rootModel: ModifiableRootModel = state.getRootModel
    val module: Module = rootModel.getModule
    val moduleName: String = module.getName

    if (!ModuleType.get(module).isInstanceOf[SbtModuleType]) return ModuleConfigurationEditor.EMPTY

    val editors = new util.ArrayList[ModuleConfigurationEditor]

    editors add new ContentEntriesEditor(moduleName, state)
    editors add new DefaultModuleConfigurationEditorFactoryImpl().createOutputEditor(state)
    editors add new ClasspathEditor(state)
//    editors add new SbtModuleEditor(state)
    editors.toArray(new Array[ModuleConfigurationEditor](editors.size))
  }
}
