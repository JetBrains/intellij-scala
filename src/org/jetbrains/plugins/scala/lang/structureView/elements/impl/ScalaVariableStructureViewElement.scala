package org.jetbrains.plugins.scala
package lang
package structureView
package elements
package impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl._

/**
* @author Alexander Podkhalyuzin
* Date: 05.05.2008
*/

class ScalaVariableStructureViewElement(named: ScNamedElement, val isInherited: Boolean) extends ScalaStructureViewElement(named, isInherited) {
  def getPresentation: ItemPresentation = {
    new ScalaVariableItemPresentation(named.nameId, isInherited)
  }

  def getChildren: Array[TreeElement] = Array.empty
}