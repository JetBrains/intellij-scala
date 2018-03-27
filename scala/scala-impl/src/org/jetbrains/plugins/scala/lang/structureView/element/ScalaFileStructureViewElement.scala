package org.jetbrains.plugins.scala.lang.structureView.element

import com.intellij.ide.util.treeView.smartTree.TreeElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/
// TODO Provide the element dynamically (or, at least, test how all that works in console)
private class ScalaFileStructureViewElement(fileProvider: () => ScalaFile) extends ScalaStructureViewElement(fileProvider()) {
  override def getPresentableText: String = fileProvider().name

  override def getChildren: Array[TreeElement] =
    fileProvider().getChildren.flatMap(ScalaStructureViewElement(_)).toArray
}
