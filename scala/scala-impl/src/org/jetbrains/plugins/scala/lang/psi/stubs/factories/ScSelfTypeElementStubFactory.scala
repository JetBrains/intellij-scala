package org.jetbrains.plugins.scala.lang.psi.stubs.factories

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScSelfTypeElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScSelfTypeElementStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScSelfTypeElementElementType
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScSelfTypeElementStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys

final class ScSelfTypeElementStubFactory(elementType: ScSelfTypeElementElementType)
  extends ScStubSerializingElementFactory[ScSelfTypeElementStub, ScSelfTypeElement](elementType) {

  override def serialize(stub: ScSelfTypeElementStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeOptionName(stub.typeText)
    dataStream.writeNames(stub.classNames)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScSelfTypeElementStub =
    new ScSelfTypeElementStubImpl(
      parentStub,
      elementType,
      name = dataStream.readNameString,
      typeText = dataStream.readOptionName,
      classNames = dataStream.readNames
    )

  override def createStubImpl(typeElement: ScSelfTypeElement, parentStub: StubElement[_ <: PsiElement]): ScSelfTypeElementStub =
    new ScSelfTypeElementStubImpl(
      parentStub,
      elementType,
      name = typeElement.name,
      typeText = typeElement.typeElement.map(_.getText),
      classNames = typeElement.classNames
    )

  override def indexStub(stub: ScSelfTypeElementStub, sink: IndexSink): Unit = {
    sink.occurrences(ScalaIndexKeys.SELF_TYPE_CLASS_NAME_KEY, stub.classNames.toSeq: _*)
  }

  override def createPsi(stub: ScSelfTypeElementStub): ScSelfTypeElement = new ScSelfTypeElementImpl(stub)
}
