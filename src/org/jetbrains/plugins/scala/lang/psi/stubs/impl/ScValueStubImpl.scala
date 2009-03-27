package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import api.statements.ScValue
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IStubElementType}
import com.intellij.util.io.StringRef

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.10.2008
 */

class ScValueStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
extends StubBaseWrapper[ScValue](parent, elemType) with ScValueStub {
  private var names: Array[StringRef] = _
  private var declaration: Boolean = false

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement],
          names: Array[String], isDeclaration: Boolean) = {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.names = for (name <- names) yield StringRef.fromString(name)
    this.declaration = isDeclaration
  }

  def getNames: Array[String] = for (name <- names) yield StringRef.toString(name)

  def isDeclaration = declaration
}