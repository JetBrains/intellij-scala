package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.psi.{PsiNameValuePair, PsiElement}
import lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import toplevel.ScNamedElement

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScNameValuePair extends ScalaPsiElement with ScNamedElement with PsiNameValuePair  {
  def nameId(): PsiElement = getNode.findChildByType(ScalaTokenTypes.tIDENTIFIER).getPsi
}