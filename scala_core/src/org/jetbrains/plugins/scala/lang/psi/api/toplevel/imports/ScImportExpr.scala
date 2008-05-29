package org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

trait ScImportExpr extends ScalaPsiElement {
  def reference = findChild(classOf[ScStableCodeReferenceElement])

  def selectorSet = findChild(classOf[ScImportSelectors])

  def selectors = {
    selectorSet match {
      case None => Seq.empty
      case Some(s) => s.selectors
    }
  }

  def singleWildcard : Boolean
}