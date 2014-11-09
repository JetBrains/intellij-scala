package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl


import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAccessModifier

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

class ScAccessModifierStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
        extends StubBaseWrapper[ScAccessModifier](parent, elemType) with ScAccessModifierStub {
  var _isPrivate: Boolean = _
  var _isProtected: Boolean = _
  var _isThis: Boolean = _
  var idText: Option[StringRef] = None

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          isPrivate: Boolean, isProtected: Boolean, isThis: Boolean, idText: Option[StringRef]) = {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this._isPrivate = isPrivate
    this._isProtected = isProtected
    this._isThis = isThis
    this.idText = idText
  }

  def isProtected: Boolean = _isProtected

  def isPrivate: Boolean = _isPrivate

  def isThis: Boolean = _isThis

  def getIdText: Option[String] = idText.map(StringRef.toString)
}