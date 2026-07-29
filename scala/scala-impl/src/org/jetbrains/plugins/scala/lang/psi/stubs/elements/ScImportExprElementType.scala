package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports.ScImportExprImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScImportExprStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScImportExprStubImpl

class ScImportExprElementType extends ScalaStubBasedElementType[ScImportExprStub, ScImportExpr](ScImportExprElementType.DebugName) {
  override def createElement(node: ASTNode): ScImportExpr = new ScImportExprImpl(node)
}

object ScImportExprElementType {
  val DebugName = "import expression"
}

class ScImportExprStubFactory(elementType: IElementType)
  extends StubSerializingElementFactory[ScImportExprStub, ScImportExpr] {

  override def serialize(stub: ScImportExprStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeOptionName(stub.referenceText)
    dataStream.writeBoolean(stub.hasWildcardSelector)
    dataStream.writeBoolean(stub.hasGivenSelector)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScImportExprStub =
    new ScImportExprStubImpl(parentStub, elementType,
      referenceText = dataStream.readOptionName,
      hasWildcardSelector = dataStream.readBoolean,
      hasGivenSelector = dataStream.readBoolean)

  override def createStub(expr: ScImportExpr, parentStub: StubElement[_ <: PsiElement]): ScImportExprStub =
    ScStubElementType.Processing.run {
      new ScImportExprStubImpl(parentStub, elementType,
        referenceText = expr.reference.map(_.getText),
        hasWildcardSelector = expr.hasWildcardSelector,
        hasGivenSelector = expr.hasGivenSelector)
    }

  override def createPsi(stub: ScImportExprStub): ScImportExpr = new ScImportExprImpl(stub)

  override def indexStub(stub: ScImportExprStub, sink: IndexSink): Unit = {}

  override def getExternalId: String = s"scala.${ScImportExprElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
