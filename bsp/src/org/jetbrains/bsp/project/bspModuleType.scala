package org.jetbrains.bsp.project

import com.intellij.ide.util.projectWizard.EmptyModuleBuilder
import com.intellij.openapi.module.{Module, ModuleConfigurationEditor, ModuleType}
import com.intellij.openapi.roots.ui.configuration._
import javax.swing.Icon
import org.jetbrains.bsp.{BspBundle, Icons}
import org.jetbrains.bsp.project.BspSyntheticModuleType._

class BspSyntheticModuleType extends ModuleType[EmptyModuleBuilder](Id) {
  override def createModuleBuilder(): EmptyModuleBuilder = new EmptyModuleBuilder
  override def getName: String = Name
  override def getDescription: String = Description
  override def getNodeIcon(isOpened: Boolean): Icon = Icons.BSP_TARGET
}

object BspSyntheticModuleType {
  def instance: BspSyntheticModuleType = new BspSyntheticModuleType

  val Id = "BSP_SYNTHETIC_MODULE"
  val Name: String = BspBundle.message("bsp.synthetic.module")
  val Description: String = BspBundle.message("bsp.synthetic.module.description")

  def unapply(m: Module): Option[Module] =
    if (ModuleType.get(m).isInstanceOf[BspSyntheticModuleType]) Some(m)
    else None
}


class BspSyntheticModuleEditorProvider extends ModuleConfigurationEditorProvider {
  def createEditors(state: ModuleConfigurationState): Array[ModuleConfigurationEditor] = {
    val module = state.getRootModel.getModule

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
