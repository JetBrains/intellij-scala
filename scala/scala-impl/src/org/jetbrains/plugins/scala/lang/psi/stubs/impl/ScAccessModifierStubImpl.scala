package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAccessModifier
import org.jetbrains.plugins.scala.lang.psi.stubs.ScAccessModifierStub

class ScAccessModifierStubImpl(parent: StubElement[_ <: PsiElement],
                               elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                               override val isProtected: Boolean,
                               override val isPrivate: Boolean,
                               override val isThis: Boolean,
                               override val idText: Option[String])
  extends StubBase[ScAccessModifier](parent, elementType) with ScAccessModifierStub