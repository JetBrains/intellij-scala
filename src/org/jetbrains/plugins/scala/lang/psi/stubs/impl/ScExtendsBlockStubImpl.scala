package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl
import api.toplevel.templates.ScExtendsBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IStubElementType}
import com.intellij.util.io.StringRef

/**
 * @author ilyas
 */

class ScExtendsBlockStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
extends StubBaseWrapper[ScExtendsBlock](parent, elemType) with ScExtendsBlockStub {
  var baseClasses: Array[StringRef] = Array[StringRef]()

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement],
          base: Array[String]) {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    baseClasses = base.map(StringRef.fromString(_))
  }

  def getBaseClasses: Array[String] = baseClasses.map(StringRef.toString(_))
}