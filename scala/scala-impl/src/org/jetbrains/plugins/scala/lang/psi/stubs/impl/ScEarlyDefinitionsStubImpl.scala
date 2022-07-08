package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions

class ScEarlyDefinitionsStubImpl(parent: StubElement[_ <: PsiElement],
                                 elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
  extends StubBase[ScEarlyDefinitions](parent, elementType) with ScEarlyDefinitionsStub