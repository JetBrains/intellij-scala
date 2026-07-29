package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScDerivesClause
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScDerivesClauseImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScDerivesClauseStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScDerivesClauseStubImpl

class ScDerivesClauseElementType extends ScalaStubBasedElementType[ScDerivesClauseStub, ScDerivesClause](ScDerivesClauseElementType.DebugName) {
  override def createElement(node: ASTNode): ScDerivesClause = new ScDerivesClauseImpl(node)
}

object ScDerivesClauseElementType {
  val DebugName = "template derives"
}

class ScDerivesClauseStubFactory(elementType: IElementType)
  extends StubSerializingElementFactory[ScDerivesClauseStub, ScDerivesClause] {

  override def serialize(stub: ScDerivesClauseStub, dataStream: StubOutputStream): Unit = {}

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScDerivesClauseStub =
    new ScDerivesClauseStubImpl(parentStub, elementType)

  override def createStub(psi: ScDerivesClause, parentStub: StubElement[_ <: PsiElement]): ScDerivesClauseStub =
    ScStubElementType.Processing.run {
      new ScDerivesClauseStubImpl(parentStub, elementType)
    }

  override def createPsi(stub: ScDerivesClauseStub): ScDerivesClause = new ScDerivesClauseImpl(stub)

  override def indexStub(stub: ScDerivesClauseStub, sink: IndexSink): Unit = {}

  override def getExternalId: String = s"scala.${ScDerivesClauseElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
