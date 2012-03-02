package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
package params

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.PsiTypeParameterList

/** 
* @author Alexander Podkhalyuzin
* @since 22.02.2008
*/
trait ScTypeParamClause extends ScalaPsiElement with PsiTypeParameterList {
  def typeParameters : Seq[ScTypeParam]

  def getTextByStub: String
}