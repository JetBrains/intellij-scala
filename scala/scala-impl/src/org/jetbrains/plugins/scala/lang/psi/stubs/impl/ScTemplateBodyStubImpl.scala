package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubBase, StubElement}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateBodyStub

class ScTemplateBodyStubImpl(parent: StubElement[_ <: PsiElement],
                             elementType: IElementType)
  extends StubBase[ScTemplateBody](parent, elementType) with ScTemplateBodyStub
