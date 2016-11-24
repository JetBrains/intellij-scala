package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.StringRefArrayExt

/**
  * User: Alexander Podkhalyuzin
  * Date: 14.10.2008
  */
class ScFunctionStubImpl(parent: StubElement[_ <: PsiElement],
                         elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                         nameRef: StringRef,
                         val isDeclaration: Boolean,
                         private val annotationsRefs: Array[StringRef],
                         protected[impl] val typeTextRef: Option[StringRef],
                         protected[impl] val bodyTextRef: Option[StringRef],
                         val hasAssign: Boolean,
                         val isImplicit: Boolean,
                         val isLocal: Boolean)
  extends ScNamedStubBase[ScFunction](parent, elementType, nameRef) with ScFunctionStub
    with ScTypeElementOwnerStub[ScFunction] with ScExpressionOwnerStub[ScFunction] {

  def annotations: Array[String] = annotationsRefs.asStrings
}