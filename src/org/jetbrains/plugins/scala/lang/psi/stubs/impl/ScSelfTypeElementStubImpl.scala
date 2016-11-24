package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.StringRefArrayExt

/**
  * User: Alexander Podkhalyuzin
  * Date: 19.06.2009
  */
class ScSelfTypeElementStubImpl(parent: StubElement[_ <: PsiElement],
                                elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                                nameRef: StringRef,
                                protected[impl] val typeTextRef: Option[StringRef],
                                private var typeNamesRefs: Array[StringRef])
  extends ScNamedStubBase[ScSelfTypeElement](parent, elementType, nameRef) with ScSelfTypeElementStub
    with ScTypeElementOwnerStub[ScSelfTypeElement] {

  override def classNames: Array[String] = typeNamesRefs.asStrings
}