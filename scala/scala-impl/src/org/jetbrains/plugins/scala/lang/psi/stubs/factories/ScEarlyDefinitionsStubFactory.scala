package org.jetbrains.plugins.scala.lang.psi.stubs.factories

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.ScEarlyDefinitionsImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScEarlyDefinitionsStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScEarlyDefinitionsElementType
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScEarlyDefinitionsStubImpl

final class ScEarlyDefinitionsStubFactory(elementType: ScEarlyDefinitionsElementType)
  extends ScStubSerializingElementFactory[ScEarlyDefinitionsStub, ScEarlyDefinitions](elementType) {

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScEarlyDefinitionsStub =
    new ScEarlyDefinitionsStubImpl(parentStub, elementType)

  override def createStubImpl(psi: ScEarlyDefinitions, parentStub: StubElement[_ <: PsiElement]): ScEarlyDefinitionsStub =
    new ScEarlyDefinitionsStubImpl(parentStub, elementType)

  override def createPsi(stub: ScEarlyDefinitionsStub): ScEarlyDefinitions = new ScEarlyDefinitionsImpl(stub)
}
