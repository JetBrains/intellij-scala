package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api._
import com.intellij.psi.{PsiElement, PsiNameValuePair}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScNamedElementBase}

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScNameValuePairBase extends ScalaPsiElementBase with ScNamedElementBase with PsiNameValuePair { this: ScNameValuePair =>
  override def getName: String = {
    if (nameId == null) ""
    else super.getName
  }

  override def nameId: PsiElement = {
    val node = getNode.findChildByType(ScalaTokenTypes.tIDENTIFIER)
    if (node == null) return null
    node.getPsi
  }

  def getLiteral: Option[ScLiteral]
}