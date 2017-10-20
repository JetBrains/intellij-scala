package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging

/**
  * @author ilyas
  */
class ScPackagingStubImpl(parent: StubElement[_ <: PsiElement],
                          elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                          private val parentPackageNameRef: StringRef,
                          private val packageNameRef: StringRef,
                          val isExplicit: Boolean)
  extends StubBase[ScPackaging](parent, elementType) with ScPackagingStub {
  def parentPackageName: String = StringRef.toString(parentPackageNameRef)

  def packageName: String = StringRef.toString(packageNameRef)
}