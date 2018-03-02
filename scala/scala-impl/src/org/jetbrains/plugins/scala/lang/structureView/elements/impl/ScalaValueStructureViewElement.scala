package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.structureView.elements.ScalaStructureViewElement
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl._

/**
* @author Alexander Podkhalyuzin
* Date: 08.05.2008
*/

class ScalaValueStructureViewElement(named: ScNamedElement, val isInherited: Boolean) extends ScalaStructureViewElement(named, isInherited) {
  override def getPresentation: ItemPresentation =
    new ScalaValueItemPresentation(named, isInherited)

  override def getChildren: Array[TreeElement] = Array.empty
}