package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
/**
 * User: Alexander Podkhalyuzin
 * Date: 21.01.2009
 */

class ScModifiersStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
        extends StubBaseWrapper[ScModifierList](parent, elemType) with ScModifiersStub {
  def getModifiers: Array[String] = modifiers

  private var modifiers: Array[String] = Array[String]()

  private var _hasExplicitModifiers: Boolean = false

  def this(parent : StubElement[ParentPsi],
          elemType : IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          modifiers: Array[String], explicitModifiers: Boolean) {
    this (parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.modifiers = modifiers
    this._hasExplicitModifiers = explicitModifiers
  }

  def hasExplicitModifiers: Boolean = _hasExplicitModifiers
}