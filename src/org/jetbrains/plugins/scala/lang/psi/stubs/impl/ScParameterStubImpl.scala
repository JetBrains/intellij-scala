package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.MaybeStringRefExt

/**
  * User: Alexander Podkhalyuzin
  * Date: 19.10.2008
  */
class ScParameterStubImpl(parent: StubElement[_ <: PsiElement],
                          elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                          nameRef: StringRef,
                          protected[impl] val typeTextRef: Option[StringRef],
                          val isStable: Boolean,
                          val isDefaultParameter: Boolean,
                          val isRepeated: Boolean,
                          val isVal: Boolean,
                          val isVar: Boolean,
                          val isCallByNameParameter: Boolean,
                          protected[impl] val bodyTextRef: Option[StringRef],
                          private val deprecatedNameRef: Option[StringRef])
  extends ScNamedStubBase[ScParameter](parent, elementType, nameRef) with ScParameterStub
    with ScTypeElementOwnerStub[ScParameter] with ScExpressionOwnerStub[ScParameter] {

  override def deprecatedName: Option[String] = deprecatedNameRef.asString
}