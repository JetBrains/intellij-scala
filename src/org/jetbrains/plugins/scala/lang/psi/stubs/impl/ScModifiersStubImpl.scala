package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.util.ArrayUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
/**
 * User: Alexander Podkhalyuzin
 * Date: 21.01.2009
 */

class ScModifiersStubImpl[ParentPsi <: PsiElement](parent : StubElement[ParentPsi],
                                                   elemType : IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                                                   modifiers: Array[String] = ArrayUtil.EMPTY_STRING_ARRAY, explicitModifiers: Boolean = false)
        extends StubBaseWrapper[ScModifierList](parent, elemType) with ScModifiersStub {
  def getModifiers: Array[String] = modifiers

  def hasExplicitModifiers: Boolean = explicitModifiers
}
