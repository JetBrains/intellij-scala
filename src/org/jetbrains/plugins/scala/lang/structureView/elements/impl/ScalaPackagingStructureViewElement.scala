package org.jetbrains.plugins.scala
package lang
package structureView
package elements
package impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl._

import _root_.scala.collection.mutable._

/**
 * @author Alexander Podkhalyuzin
 * @deprecated
 * Date : 05.05.2008
 */

class ScalaPackagingStructureViewElement(private val element: ScPackaging) extends ScalaStructureViewElement(element, false) {
  def getPresentation: ItemPresentation = {
    new ScalaPackagingItemPresentation(element);
  }

  def getChildren: Array[TreeElement] = {
    val children = new ArrayBuffer[ScalaStructureViewElement]
    for (td <- element.immediateTypeDefinitions) {
      children += new ScalaTypeDefinitionStructureViewElement(td)
    }
    for (p <- element.packagings) {
      children += new ScalaPackagingStructureViewElement(p)
    }

    children.toArray
  }
}