package org.jetbrains.plugins.scala.lang.psi.stubs.elements


import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScIdList
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScIdListImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScIdListStub
import org.jetbrains.plugins.scala.lang.psi.stubs.factories.ScStubSerializingElementFactory
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScIdListStubImpl

final class ScIdListElementType extends ScStubElementType[ScIdList]("id list") {
  override def createElement(node: ASTNode): ScIdList = new ScIdListImpl(node)
}

final class ScIdListStubFactory(elementType: ScIdListElementType)
  extends ScStubSerializingElementFactory[ScIdListStub, ScIdList](elementType) {

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScIdListStub =
    new ScIdListStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], elementType)

  override def createStubImpl(psi: ScIdList, parentStub: StubElement[_ <: PsiElement]): ScIdListStub =
    new ScIdListStubImpl(parentStub, elementType)

  override def createPsi(stub: ScIdListStub): ScIdList = new ScIdListImpl(stub)
}
