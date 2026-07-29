package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubBase, StubElement}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotations
import org.jetbrains.plugins.scala.lang.psi.stubs.ScAnnotationsStub

class ScAnnotationsStubImpl(parent: StubElement[_ <: PsiElement],
                            elementType: IElementType)
  extends StubBase[ScAnnotations](parent, elementType) with ScAnnotationsStub
