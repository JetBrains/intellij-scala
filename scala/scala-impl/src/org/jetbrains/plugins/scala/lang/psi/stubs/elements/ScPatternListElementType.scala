package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPatternList
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScPatternListImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPatternListStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScPatternListStubImpl

class ScPatternListElementType extends ScalaStubBasedElementType[ScPatternListStub, ScPatternList](ScPatternListElementType.DebugName) {
  override def createElement(node: ASTNode): ScPatternList = new ScPatternListImpl(node)
}

object ScPatternListElementType {
  val DebugName = "pattern list"
}

class ScPatternListStubFactory(elementType: IElementType)
  extends StubSerializingElementFactory[ScPatternListStub, ScPatternList] {

  override def serialize(stub: ScPatternListStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.simplePatterns)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScPatternListStub =
    new ScPatternListStubImpl(parentStub, elementType, simplePatterns = dataStream.readBoolean)

  override def createStub(patterns: ScPatternList, parentStub: StubElement[_ <: PsiElement]): ScPatternListStub =
    ScStubElementType.Processing.run {
      new ScPatternListStubImpl(parentStub, elementType, simplePatterns = patterns.simplePatterns)
    }

  override def createPsi(stub: ScPatternListStub): ScPatternList = new ScPatternListImpl(stub)

  override def indexStub(stub: ScPatternListStub, sink: IndexSink): Unit = {}

  override def getExternalId: String = s"scala.${ScPatternListElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
