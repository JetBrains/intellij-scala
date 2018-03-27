package org.jetbrains.plugins.scala.lang.structureView.element

import com.intellij.ide.util.treeView.smartTree.TreeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging

/**
 * @author Alexander Podkhalyuzin
 * @deprecated
 * Date : 05.05.2008
 */

private class ScalaPackagingStructureViewElement(packaging: ScPackaging) extends ScalaStructureViewElement(packaging, inherited = false) {
  def getPresentableText: String = ""

  override def getChildren: Array[TreeElement] =
    (packaging.immediateTypeDefinitions ++ packaging.packagings).flatMap(ScalaStructureViewElement(_)).toArray
}
