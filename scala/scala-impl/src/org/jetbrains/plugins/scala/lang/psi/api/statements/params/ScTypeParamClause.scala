package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements
package params

import com.intellij.psi.{PsiTypeParameter, PsiTypeParameterList}

trait ScTypeParamClause extends ScalaPsiElement with PsiTypeParameterList {
  def typeParameters : Seq[ScTypeParam]

  def getTextByStub: String

  override def getTypeParameterIndex(typeParameter: PsiTypeParameter): Int = typeParameters.indexOf(typeParameter)

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitTypeParameterClause(this)
  }
}