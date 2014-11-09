package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl


import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelectors

/**
 * User: Alexander Podkhalyuzin
 * Date: 20.06.2009
 */

class ScImportSelectorsStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                         elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
        extends StubBaseWrapper[ScImportSelectors](parent, elemType) with ScImportSelectorsStub {
  var singleWildcard: Boolean = _

  def this(parent: StubElement[ParentPsi],
           elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement], singleWildcard: Boolean) {
    this (parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.singleWildcard = singleWildcard
  }

  def hasWildcard: Boolean = singleWildcard
}