package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.{PsiNameValuePair, PsiElement}
import lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import toplevel.ScNamedElement

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScNameValuePair extends ScalaPsiElement with ScNamedElement with PsiNameValuePair  {
  override def getName: String = {
    if (nameId == null) return ""
    else super.getName
  }

  def nameId(): PsiElement = {
    val node = getNode.findChildByType(ScalaTokenTypes.tIDENTIFIER)
    if (node == null) return null
    node.getPsi
  }
}