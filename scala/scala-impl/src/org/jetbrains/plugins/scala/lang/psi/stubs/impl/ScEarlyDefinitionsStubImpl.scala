package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubBase, StubElement}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.stubs.ScEarlyDefinitionsStub

class ScEarlyDefinitionsStubImpl(parent: StubElement[_ <: PsiElement],
                                 elementType: IElementType)
  extends StubBase[ScEarlyDefinitions](parent, elementType) with ScEarlyDefinitionsStub
