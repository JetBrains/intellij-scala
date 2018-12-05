package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging

/**
  * @author ilyas
  */
class ScPackagingStubImpl(parent: StubElement[_ <: PsiElement],
                          elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                          val parentPackageName: String,
                          val packageName: String,
                          val isExplicit: Boolean)
  extends StubBase[ScPackaging](parent, elementType) with ScPackagingStub