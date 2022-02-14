package org.jetbrains.bsp.project

import com.intellij.openapi.module.{ModuleConfigurationEditor, ModuleType}
import com.intellij.openapi.roots.ui.configuration._
import org.jetbrains.bsp.project.BspSyntheticModuleType._
import org.jetbrains.bsp.{BspBundle, Icons}
import org.jetbrains.sbt.project.DummyModuleBuilder

import javax.swing.Icon

class BspSyntheticModuleType extends ModuleType[DummyModuleBuilder](Id) {
  override def createModuleBuilder: DummyModuleBuilder = new DummyModuleBuilder
  override def getName: String = Name
  override def getDescription: String = Description
  override def getNodeIcon(isOpened: Boolean): Icon = Icons.BSP_TARGET
}

object BspSyntheticModuleType {
  val Id = "BSP_SYNTHETIC_MODULE"
  val Name: String = BspBundle.message("bsp.synthetic.module")
  val Description: String = BspBundle.message("bsp.synthetic.module.description")
}


class BspSyntheticModuleEditorProvider extends ModuleConfigurationEditorProvider {
  override def createEditors(state: ModuleConfigurationState): Array[ModuleConfigurationEditor] = {
    val module = state.getCurrentRootModel.getModule

    ModuleType.get(module) match {
      case _: BspSyntheticModuleType => Array(
        new ContentEntriesEditor(module.getName, state),
        new DefaultModuleConfigurationEditorFactoryImpl().createOutputEditor(state),
        new ClasspathEditor(state)
      )
      case _ =>
        ModuleConfigurationEditor.EMPTY
    }
  }
}
