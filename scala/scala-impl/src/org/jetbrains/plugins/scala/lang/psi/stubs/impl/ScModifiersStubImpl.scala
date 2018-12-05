package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList

/**
  * User: Alexander Podkhalyuzin
  * Date: 21.01.2009
  */
class ScModifiersStubImpl(parent: StubElement[_ <: PsiElement],
                          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                          val hasExplicitModifiers: Boolean,
                          val modifiers: Array[String])
  extends StubBase[ScModifierList](parent, elemType) with ScModifiersStub
