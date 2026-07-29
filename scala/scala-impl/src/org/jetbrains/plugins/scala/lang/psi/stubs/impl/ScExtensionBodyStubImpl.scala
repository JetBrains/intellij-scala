package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubBase, StubElement}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtensionBody
import org.jetbrains.plugins.scala.lang.psi.stubs.ScExtensionBodyStub

class ScExtensionBodyStubImpl(
  parent:      StubElement[_ <: PsiElement],
  elementType: IElementType
) extends StubBase[ScExtensionBody](parent, elementType)
    with ScExtensionBodyStub
