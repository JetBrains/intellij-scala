package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import api.base.ScModifierList
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IStubElementType}
import com.intellij.util.io.StringRef
/**
 * User: Alexander Podkhalyuzin
 * Date: 21.01.2009
 */

class ScModifiersStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                          elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
        extends StubBaseWrapper[ScModifierList](parent, elemType) with ScModifiersStub {
  def getModifiers(): Array[String] = modifiers.map(StringRef.toString(_))

  private var modifiers: Array[StringRef] = Array[StringRef]()

  def this(parent : StubElement[ParentPsi],
          elemType : IStubElementType[_ <: StubElement[_], _ <: PsiElement],
          modifiers: Array[String]) {
    this (parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.modifiers = modifiers.map(StringRef.fromString(_))
  }
}