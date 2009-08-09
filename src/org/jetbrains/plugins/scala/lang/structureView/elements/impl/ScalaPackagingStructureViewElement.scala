package org.jetbrains.plugins.scala
package lang
package structureView
package elements
package impl

import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl._
import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import com.intellij.psi._

import _root_.scala.collection.mutable._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._

/**
* @author Alexander Podkhalyuzin
* Date: 05.05.2008
*/

class ScalaPackagingStructureViewElement(private val element: ScPackaging) extends ScalaStructureViewElement(element, false) {
  def getPresentation(): ItemPresentation = {
    return new ScalaPackagingItemPresentation(element);
  }

  def getChildren(): Array[TreeElement] = {
    val children = new ArrayBuffer[ScalaStructureViewElement]
    for (td <- element.immediateTypeDefinitions) {
      children += new ScalaTypeDefinitionStructureViewElement(td)
    }
    for (p <- element.packagings) {
      children += new ScalaPackagingStructureViewElement(p)
    }

    return children.toArray
  }
}