package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.structureView.elements.impl.ScalaPackagingStructureViewElement.Presentation

/**
 * @author Alexander Podkhalyuzin
 * @deprecated
 * Date : 05.05.2008
 */

class ScalaPackagingStructureViewElement(packaging: ScPackaging) extends ScalaStructureViewElement(packaging, inherited = false) {
  override def getPresentation: ItemPresentation = new Presentation(packaging)

  override def getChildren: Array[TreeElement] =
    (packaging.immediateTypeDefinitions ++ packaging.packagings).flatMap(ScalaStructureViewElement(_)).toArray
}

object ScalaPackagingStructureViewElement {
  private class Presentation(packaging: ScPackaging) extends ScalaItemPresentation(packaging) {
    def getPresentableText: String = ""
  }
}