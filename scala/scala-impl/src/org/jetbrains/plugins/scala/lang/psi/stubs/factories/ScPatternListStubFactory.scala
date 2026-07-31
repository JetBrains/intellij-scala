package org.jetbrains.plugins.scala.lang.psi.stubs.factories

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPatternList
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScPatternListImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPatternListStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScPatternListElementType
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScPatternListStubImpl

final class ScPatternListStubFactory(elementType: ScPatternListElementType)
  extends ScStubSerializingElementFactory[ScPatternListStub, ScPatternList](elementType) {

  override def serialize(stub: ScPatternListStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.simplePatterns)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScPatternListStub =
    new ScPatternListStubImpl(parentStub, elementType, simplePatterns = dataStream.readBoolean)

  override def createStubImpl(patterns: ScPatternList, parentStub: StubElement[_ <: PsiElement]): ScPatternListStub =
    new ScPatternListStubImpl(parentStub, elementType, simplePatterns = patterns.simplePatterns)

  override def createPsi(stub: ScPatternListStub): ScPatternList = new ScPatternListImpl(stub)
}
