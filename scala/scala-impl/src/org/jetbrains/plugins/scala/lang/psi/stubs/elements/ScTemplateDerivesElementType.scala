package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateDerives
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScTemplateDerivesImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTemplateDerivesStubImpl

class ScTemplateDerivesElementType extends ScStubElementType[ScTemplateDerivesStub, ScTemplateDerives]("template derives") {

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScTemplateDerivesStub =
    new ScTemplateDerivesStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this)

  override def createStubImpl(psi: ScTemplateDerives, parentStub: StubElement[_ <: PsiElement]): ScTemplateDerivesStub =
    new ScTemplateDerivesStubImpl(parentStub, this)

  override def createElement(node: ASTNode): ScTemplateDerives = new ScTemplateDerivesImpl(node)

  override def createPsi(stub: ScTemplateDerivesStub): ScTemplateDerives = new ScTemplateDerivesImpl(stub)
}
