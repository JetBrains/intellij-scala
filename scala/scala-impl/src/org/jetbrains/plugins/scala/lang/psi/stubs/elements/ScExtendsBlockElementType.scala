package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScExtendsBlockImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScExtendsBlockStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScExtendsBlockStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors

import scala.collection.immutable.ArraySeq

class ScExtendsBlockElementType extends ScalaStubBasedElementType[ScExtendsBlockStub, ScExtendsBlock](ScExtensionBodyElementType.DebugName) {
  override def createElement(node: ASTNode): ScExtendsBlock = new ScExtendsBlockImpl(node)
}

object ScExtendsBlockElementType {
  val DebugName = "extends block"
}

class ScExtendsBlockStubFactory(elementType: IElementType) extends StubSerializingElementFactory[ScExtendsBlockStub, ScExtendsBlock] {

  override def serialize(stub: ScExtendsBlockStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeNames(stub.baseClasses)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScExtendsBlockStub =
    new ScExtendsBlockStubImpl(parentStub, elementType, baseClasses = ArraySeq.unsafeWrapArray(dataStream.readNames))

  override def createStub(psi: ScExtendsBlock, parentStub: StubElement[_ <: PsiElement]): ScExtendsBlockStub =
    ScStubElementType.Processing.run {
      new ScExtendsBlockStubImpl(parentStub, elementType, baseClasses = ScalaInheritors.directSupersNames(psi))
    }

  override def createPsi(stub: ScExtendsBlockStub): ScExtendsBlock = new ScExtendsBlockImpl(stub)

  override def indexStub(stub: ScExtendsBlockStub, sink: IndexSink): Unit = {
    sink.occurrences(ScalaIndexKeys.SUPER_CLASS_NAME_KEY, stub.baseClasses: _*)
  }

  override def getExternalId: String = s"scala.${ScExtendsBlockElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
