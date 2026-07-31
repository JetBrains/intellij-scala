package org.jetbrains.plugins.scala.lang.psi.stubs.factories

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScTemplateBodyImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateBodyStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateBodyElementType
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTemplateBodyStubImpl

final class ScTemplateBodyStubFactory(elementType: ScTemplateBodyElementType)
  extends ScStubSerializingElementFactory[ScTemplateBodyStub, ScTemplateBody](elementType) {

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScTemplateBodyStub =
    new ScTemplateBodyStubImpl(parentStub, elementType)

  override def createStubImpl(templateBody: ScTemplateBody, parentStub: StubElement[_ <: PsiElement]): ScTemplateBodyStub =
    new ScTemplateBodyStubImpl(parentStub, elementType)

  override def createPsi(stub: ScTemplateBodyStub): ScTemplateBody = new ScTemplateBodyImpl(stub)
}
