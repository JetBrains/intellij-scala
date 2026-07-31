package org.jetbrains.plugins.scala.lang.psi.stubs.factories

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScExtendsBlockImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScExtendsBlockStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScExtendsBlockElementType
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScExtendsBlockStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors

import scala.collection.immutable.ArraySeq

final class ScExtendsBlockStubFactory(elementType: ScExtendsBlockElementType) extends ScStubSerializingElementFactory[ScExtendsBlockStub, ScExtendsBlock](elementType) {
  override def serialize(stub: ScExtendsBlockStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeNames(stub.baseClasses)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScExtendsBlockStub =
    new ScExtendsBlockStubImpl(parentStub, elementType, baseClasses = ArraySeq.unsafeWrapArray(dataStream.readNames))

  override def createStubImpl(psi: ScExtendsBlock, parentStub: StubElement[_ <: PsiElement]): ScExtendsBlockStub =
    new ScExtendsBlockStubImpl(parentStub, elementType, baseClasses = ScalaInheritors.directSupersNames(psi))

  override def createPsi(stub: ScExtendsBlockStub): ScExtendsBlock = new ScExtendsBlockImpl(stub)

  override def indexStub(stub: ScExtendsBlockStub, sink: IndexSink): Unit = {
    sink.occurrences(ScalaIndexKeys.SUPER_CLASS_NAME_KEY, stub.baseClasses: _*)
  }
}
