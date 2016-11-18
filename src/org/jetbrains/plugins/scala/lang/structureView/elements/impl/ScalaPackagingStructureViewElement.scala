package org.jetbrains.plugins.scala
package lang
package structureView
package elements
package impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl._

import _root_.scala.collection.mutable._

/**
 * @author Alexander Podkhalyuzin
 * @deprecated
 * Date : 05.05.2008
 */

class ScalaPackagingStructureViewElement(pack: ScPackaging) extends ScalaStructureViewElement(pack, false) {
  def getPresentation: ItemPresentation = {
    new ScalaPackagingItemPresentation(pack)
  }

  def getChildren: Array[TreeElement] = {
    val children = new ArrayBuffer[ScalaStructureViewElement[_]]
    for (td <- pack.immediateTypeDefinitions) {
      children += new ScalaTypeDefinitionStructureViewElement(td)
    }
    for (p <- pack.packagings) {
      children += new ScalaPackagingStructureViewElement(p)
    }

    children.toArray
  }
}