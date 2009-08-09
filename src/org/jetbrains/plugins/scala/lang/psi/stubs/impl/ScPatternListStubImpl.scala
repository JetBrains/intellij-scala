package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl


import api.base.ScPatternList
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IStubElementType}
/**
 * User: Alexander Podkhalyuzin
 * Date: 17.07.2009
 */

class ScPatternListStubImpl[ParentPsi <: PsiElement] private (parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
extends StubBaseWrapper[ScPatternList](parent, elemType) with ScPatternListStub {
  var patternsSimple: Boolean = false

  def this(parent: StubElement[ParentPsi], elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement],
           patternsSimple: Boolean) {
    this(parent, elemType)
    this.patternsSimple = patternsSimple
  }

  def allPatternsSimple: Boolean = patternsSimple
}