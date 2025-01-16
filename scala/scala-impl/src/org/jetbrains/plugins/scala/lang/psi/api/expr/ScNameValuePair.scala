package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.psi.PsiNameValuePair
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

trait ScNameValuePair extends ScalaPsiElement with ScNamedElement with PsiNameValuePair  {
  def getLiteral: Option[ScLiteral]
}