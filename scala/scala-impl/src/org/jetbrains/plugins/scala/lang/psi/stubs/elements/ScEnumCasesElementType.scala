package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCases
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScEnumCasesImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScEnumCasesStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScEnumCasesStubImpl

final class ScEnumCasesElementType extends ScalaStubBasedElementType[ScEnumCasesStub, ScEnumCases](ScEnumCasesElementType.DebugName) {
  override def createElement(node: ASTNode): ScEnumCases = new ScEnumCasesImpl(null, null, node)
}

object ScEnumCasesElementType {
  val DebugName = "ScEnumCases"
}

class ScEnumCasesStubFactory(elementType: IElementType) extends StubSerializingElementFactory[ScEnumCasesStub, ScEnumCases] {

  override def createPsi(stub: ScEnumCasesStub): ScEnumCases =
    new ScEnumCasesImpl(stub, new ScEnumCasesElementType, null)

  override def createStub(psi: ScEnumCases, parentStub: StubElement[_ <: PsiElement]): ScEnumCasesStub =
    ScStubElementType.Processing.run {
      new ScEnumCasesStubImpl(parentStub, elementType)
    }

  override def serialize(stub: ScEnumCasesStub, dataStream: StubOutputStream): Unit = {}

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScEnumCasesStub =
    new ScEnumCasesStubImpl(parentStub, elementType)

  override def indexStub(stub: ScEnumCasesStub, sink: IndexSink): Unit = {}

  override def getExternalId: String = s"scala.${ScEnumCasesElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
