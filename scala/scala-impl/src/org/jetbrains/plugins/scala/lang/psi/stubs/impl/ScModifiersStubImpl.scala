package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.StringRefArrayExt

/**
  * User: Alexander Podkhalyuzin
  * Date: 21.01.2009
  */
class ScModifiersStubImpl(parent: StubElement[_ <: PsiElement],
                          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                          val hasExplicitModifiers: Boolean,
                          val modifiersRefs: Array[StringRef])
  extends StubBase[ScModifierList](parent, elemType) with ScModifiersStub {
  override def modifiers: Array[String] = modifiersRefs.asStrings
}
