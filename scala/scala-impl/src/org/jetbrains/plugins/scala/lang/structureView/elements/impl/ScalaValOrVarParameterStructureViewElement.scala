package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.structureView.elements.ScalaStructureViewElement
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl._

class ScalaValOrVarParameterStructureViewElement(parameter: ScClassParameter, inherited: Boolean)
  extends ScalaStructureViewElement(parameter, inherited) {

  override def getPresentation: ItemPresentation =
    new ScalaValOrVarParameterItemPresentation(parameter, inherited)

  override def getChildren: Array[TreeElement] = TreeElement.EMPTY_ARRAY
}