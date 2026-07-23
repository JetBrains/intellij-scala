package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubBase, StubElement}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAccessModifier
import org.jetbrains.plugins.scala.lang.psi.stubs.ScAccessModifierStub

class ScAccessModifierStubImpl(parent: StubElement[_ <: PsiElement],
                               elementType: IElementType,
                               override val isProtected: Boolean,
                               override val isPrivate: Boolean,
                               override val isThis: Boolean,
                               override val idText: Option[String])
  extends StubBase[ScAccessModifier](parent, elementType) with ScAccessModifierStub
