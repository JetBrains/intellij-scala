package org.jetbrains.plugins.scala
package lang
package structureView
package elements
package impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaFunctionStructureViewElement(func: ScFunction, val isInherited: Boolean) extends ScalaStructureViewElement(func, isInherited) {

  def getPresentation: ItemPresentation = {
    new ScalaFunctionItemPresentation(func, isInherited)
  }

  def getChildren: Array[TreeElement] = Array()
}