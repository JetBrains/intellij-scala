package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.structureView.ScalaElementPresentation
import org.jetbrains.plugins.scala.lang.structureView.elements.ScalaStructureViewElement
import org.jetbrains.plugins.scala.lang.structureView.elements.impl.ScalaPackagingStructureViewElement.Presentation

import _root_.scala.collection.mutable._

/**
 * @author Alexander Podkhalyuzin
 * @deprecated
 * Date : 05.05.2008
 */

class ScalaPackagingStructureViewElement(packaging: ScPackaging) extends ScalaStructureViewElement(packaging, inherited = false) {
  override def getPresentation: ItemPresentation = new Presentation(packaging)

  override def getChildren: Array[TreeElement] = {
    val children = new ArrayBuffer[ScalaStructureViewElement[_]]
    for (td <- packaging.immediateTypeDefinitions) {
      children += new ScalaTypeDefinitionStructureViewElement(td)
    }
    for (p <- packaging.packagings) {
      children += new ScalaPackagingStructureViewElement(p)
    }

    children.toArray
  }
}

private object ScalaPackagingStructureViewElement {
  class Presentation(element: ScPackaging) extends ScalaItemPresentation(element) {
    def getPresentableText: String = ScalaElementPresentation.getPackagingPresentableText(element)
  }
}