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
* Date: 08.05.2008
*/

class ScalaValueStructureViewElement(named: ScNamedElement, val isInherited: Boolean) extends ScalaStructureViewElement(named, isInherited) {
  def getPresentation: ItemPresentation = {
    new ScalaValueItemPresentation(named.nameId, isInherited)
  }

  def getChildren: Array[TreeElement] = Array.empty
}