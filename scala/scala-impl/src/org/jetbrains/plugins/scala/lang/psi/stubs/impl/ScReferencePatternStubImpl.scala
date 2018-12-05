package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern

/**
  * User: Alexander Podkhalyuzin
  * Date: 17.07.2009
  */
class ScReferencePatternStubImpl(parent: StubElement[_ <: PsiElement],
                                 elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                                 name: String)
  extends ScNamedStubBase[ScReferencePattern](parent, elementType, name) with ScReferencePatternStub