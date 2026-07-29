package org.jetbrains.plugins.scala.lang.psi.stubs.elements


import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs._
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScIdList
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScIdListImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScIdListStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScIdListStubImpl

class ScIdListElementType extends ScalaStubBasedElementType[ScIdListStub, ScIdList](ScIdListElementType.DebugName) {
  override def createElement(node: ASTNode): ScIdList = new ScIdListImpl(node)
}

object ScIdListElementType {
  val DebugName = "id list"
}

class ScIdListStubFactory(elementType: IElementType)
  extends StubSerializingElementFactory[ScIdListStub, ScIdList] {

  override def serialize(stub: ScIdListStub, dataStream: StubOutputStream): Unit = {}

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScIdListStub =
    new ScIdListStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], elementType)

  override def createStub(psi: ScIdList, parentStub: StubElement[_ <: PsiElement]): ScIdListStub =
    ScStubElementType.Processing.run {
      new ScIdListStubImpl(parentStub, elementType)
    }

  override def createPsi(stub: ScIdListStub): ScIdList = new ScIdListImpl(stub)

  override def indexStub(stub: ScIdListStub, sink: IndexSink): Unit = {}

  override def getExternalId: String = s"scala.${ScIdListElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
