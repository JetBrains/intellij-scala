package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.structureView.elements.impl.ScalaFileStructureViewElement.Presentation

import scala.collection._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/
// TODO Provide the element dynamically (or, at least, test how all that works in console)
class ScalaFileStructureViewElement(fileProvider: () => ScalaFile) extends ScalaStructureViewElement(fileProvider()) {
  override def getPresentation: ItemPresentation = new Presentation(fileProvider())

  override def getChildren: Array[TreeElement] =
    fileProvider().getChildren.flatMap(ScalaStructureViewElement(_)).toArray
}

object ScalaFileStructureViewElement {
  private class Presentation(file: ScalaFile) extends ScalaItemPresentation(file) {
    override def getPresentableText: String = file.name
  }
}