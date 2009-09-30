package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import api.statements.params.ScParameter
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IStubElementType}
import com.intellij.util.io.StringRef

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.10.2008
 */

class ScParameterStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
extends StubBaseWrapper[ScParameter](parent, elemType) with ScParameterStub {
  private var name: StringRef = _
  private var typeText: StringRef = _
  private var stable: Boolean = false
  private var default: Boolean = false
  private var repeated: Boolean = false

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          name: String, typeText: String, stable: Boolean, default: Boolean, repeated: Boolean) = {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.name = StringRef.fromString(name)
    this.typeText = StringRef.fromString(typeText)
    this.stable = stable
    this.default = default
    this.repeated = repeated
  }

  def getName: String = StringRef.toString(name)

  def getTypeText: String = StringRef.toString(typeText)

  def isStable: Boolean = stable

  def isDefaultParam: Boolean = default

  def isRepeated: Boolean = repeated
}