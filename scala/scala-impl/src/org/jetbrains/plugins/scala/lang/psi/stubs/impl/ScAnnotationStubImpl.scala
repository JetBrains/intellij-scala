package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation

/**
  * User: Alexander Podkhalyuzin
  * Date: 22.06.2009
  */
class ScAnnotationStubImpl(parent: StubElement[_ <: PsiElement],
                           elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                           val name: Option[String],
                           val typeText: Option[String])
  extends StubBase[ScAnnotation](parent, elementType) with ScAnnotationStub
