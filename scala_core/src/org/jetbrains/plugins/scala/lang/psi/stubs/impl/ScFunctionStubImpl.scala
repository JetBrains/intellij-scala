package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import api.statements.ScFunction
import api.toplevel.typedef.ScTemplateDefinition
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IStubElementType}
import com.intellij.util.io.StringRef

/**
 *  User: Alexander Podkhalyuzin
 *  Date: 14.10.2008
 */

class ScFunctionStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
extends StubBaseWrapper[ScFunction](parent, elemType) with ScFunctionStub {
  private var name: StringRef = _
  private var declaration: Boolean = false
  private var annotations: Seq[String] = Seq.empty

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement],
          name: String, isDeclaration: Boolean, annotations: Seq[String]) = {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.name = StringRef.fromString(name)
    this.declaration = isDeclaration
    this.annotations = annotations
  }

  def getName: String = StringRef.toString(name)

  def isDeclaration = declaration

  def getAnnotations: Seq[String] = annotations
}