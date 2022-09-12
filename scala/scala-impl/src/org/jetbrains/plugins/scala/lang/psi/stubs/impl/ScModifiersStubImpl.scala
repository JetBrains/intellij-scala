package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.stubs.ScModifiersStub
import org.jetbrains.plugins.scala.util.EnumSet.EnumSet

class ScModifiersStubImpl(parent: StubElement[_ <: PsiElement],
                          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                          override val modifiers: EnumSet[ScalaModifier])
  extends StubBase[ScModifierList](parent, elemType) with ScModifiersStub
