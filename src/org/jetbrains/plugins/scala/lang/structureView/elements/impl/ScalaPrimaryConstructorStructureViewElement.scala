package org.jetbrains.plugins.scala
package lang
package structureView
package elements
package impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl._

/**
* @author Alexander Podkhalyuzin
* Date: 16.05.2008
*/

class ScalaPrimaryConstructorStructureViewElement(pc: ScPrimaryConstructor) extends ScalaStructureViewElement(pc, false) {

  def getPresentation: ItemPresentation = {
    new ScalaPrimaryConstructorItemPresentation(pc)
  }

  def getChildren: Array[TreeElement] = {
    new Array[TreeElement](0)
  }
}