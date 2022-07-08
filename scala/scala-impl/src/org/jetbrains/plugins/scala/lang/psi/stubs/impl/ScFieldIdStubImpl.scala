package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId

class ScFieldIdStubImpl(parent: StubElement[_ <: PsiElement],
                        elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                        name: String)
  extends ScNamedStubBase[ScFieldId](parent, elementType, name) with ScFieldIdStub