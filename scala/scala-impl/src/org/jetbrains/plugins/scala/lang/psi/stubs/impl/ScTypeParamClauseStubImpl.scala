package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause

/**
  * User: Alexander Podkhalyuzin
  * Date: 17.06.2009
  */
class ScTypeParamClauseStubImpl(parent: StubElement[_ <: PsiElement],
                                elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                                private val typeParameterClauseTextRef: StringRef)
  extends StubBase[ScTypeParamClause](parent, elementType) with ScTypeParamClauseStub {
  def typeParameterClauseText: String = StringRef.toString(typeParameterClauseTextRef)
}