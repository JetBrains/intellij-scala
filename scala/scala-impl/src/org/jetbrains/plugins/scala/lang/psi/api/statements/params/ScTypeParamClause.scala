package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
package params

import com.intellij.psi.{PsiTypeParameter, PsiTypeParameterList}

/** 
* @author Alexander Podkhalyuzin
* @since 22.02.2008
*/
trait ScTypeParamClause extends ScalaPsiElement with PsiTypeParameterList {
  def typeParameters : Seq[ScTypeParam]

  def getTextByStub: String

  def getTypeParameters: Array[PsiTypeParameter] = typeParameters.toArray

  def getTypeParameterIndex(typeParameter: PsiTypeParameter): Int = typeParameters.indexOf(typeParameter)
}