package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScExportStmt, ScImportOrExportStmt, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports.{ScExportStmtImpl, ScImportStmtImpl}
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.{ScExportStmtStubImpl, ScImportStmtStubImpl}
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.TOP_LEVEL_EXPORT_BY_PKG_KEY
import org.jetbrains.plugins.scala.lang.psi.stubs.{ScExportStmtStub, ScImportOrExportStmtStub, ScImportStmtStub}

abstract sealed class ScImportOrExportStmtElementType[
  P <: ScImportOrExportStmt,
  S >: Null <: ScImportOrExportStmtStub[P],
](
  debugName: String
) extends ScalaStubBasedElementType[S, P](debugName)

class ScImportStmtElementType extends ScImportOrExportStmtElementType[ScImportStmt, ScImportStmtStub](ScImportStmtElementType.DebugName) {
  override def createElement(node: ASTNode): ScImportStmt = new ScImportStmtImpl(null, null, node, ScImportStmtElementType.DebugName)
}

object ScImportStmtElementType {
  val DebugName = "ScImportStatement"
}

class ScImportStmtStubFactory(elementType: ScImportStmtElementType)
  extends StubSerializingElementFactory[ScImportStmtStub, ScImportStmt] {

  override def createPsi(stub: ScImportStmtStub): ScImportStmt =
    new ScImportStmtImpl(stub, elementType, null, ScImportStmtElementType.DebugName)

  override final def createStub(statement: ScImportStmt, parentStub: StubElement[_ <: PsiElement]): ScImportStmtStub =
    ScStubElementType.Processing.run {
      new ScImportStmtStubImpl(parentStub, elementType, importText = statement.getText)
    }

  override final def serialize(stub: ScImportStmtStub, dataStream: StubOutputStream): Unit =
    dataStream.writeName(stub.importText)

  override final def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScImportStmtStub =
    new ScImportStmtStubImpl(parentStub, elementType, importText = dataStream.readNameString)

  override def indexStub(stub: ScImportStmtStub, sink: IndexSink): Unit = {}

  override def getExternalId: String = s"scala.${ScImportStmtElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}

class ScExportStmtElementType extends ScImportOrExportStmtElementType[ScExportStmt, ScExportStmtStub](ScExportStmtElementType.DebugName) {
  override def createElement(node: ASTNode): ScExportStmt = new ScExportStmtImpl(null, null, node, ScExportStmtElementType.DebugName)
}

object ScExportStmtElementType {
  val DebugName = "ScExportStatement"
}

class ScExportStmtStubFactory(elementType: ScExportStmtElementType)
  extends StubSerializingElementFactory[ScExportStmtStub, ScExportStmt] {

  override def createStub(statement: ScExportStmt, parentStub: StubElement[_ <: PsiElement]): ScExportStmtStub =
    ScStubElementType.Processing.run {
      new ScExportStmtStubImpl(
        parentStub,
        elementType,
        importText        = statement.getText,
        isTopLevel        = statement.isTopLevel,
        topLevelQualifier = statement.topLevelQualifier
      )
    }

  override def createPsi(stub: ScExportStmtStub): ScExportStmt =
    new ScExportStmtImpl(stub, elementType, null, ScExportStmtElementType.DebugName)

  override def serialize(stub: ScExportStmtStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.importText)
    dataStream.writeBoolean(stub.isTopLevel)
    dataStream.writeOptionName(stub.topLevelQualifier)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScExportStmtStub =
    new ScExportStmtStubImpl(
      parentStub,
      elementType,
      importText        = dataStream.readNameString,
      isTopLevel        = dataStream.readBoolean,
      topLevelQualifier = dataStream.readOptionName
    )

  override def indexStub(stub: ScExportStmtStub, sink: IndexSink): Unit = {
    if (stub.isTopLevel) {
      stub.topLevelQualifier.foreach(qual =>
        sink.occurrence(TOP_LEVEL_EXPORT_BY_PKG_KEY, qual)
      )
    }
  }

  override def getExternalId: String = s"scala.${ScExportStmtElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
