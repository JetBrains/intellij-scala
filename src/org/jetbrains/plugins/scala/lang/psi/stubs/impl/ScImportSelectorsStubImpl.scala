package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl


import api.toplevel.imports.ScImportSelectors
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import stubs.ScImportSelectorsStub

/**
 * User: Alexander Podkhalyuzin
 * Date: 20.06.2009
 */

class ScImportSelectorsStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                         elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
        extends StubBaseWrapper[ScImportSelectors](parent, elemType) with ScImportSelectorsStub {
  var singleWildcard: Boolean = _

  def this(parent: StubElement[ParentPsi],
           elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement], singleWildcard: Boolean) {
    this (parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.singleWildcard = singleWildcard
  }

  def hasWildcard: Boolean = singleWildcard
}