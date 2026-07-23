package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScExportStmt, ScImportOrExportStmt, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports.{ScExportStmtImpl, ScImportStmtImpl}
import org.jetbrains.plugins.scala.lang.psi.stubs.factories.ScStubSerializingElementFactory
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.{ScExportStmtStubImpl, ScImportStmtStubImpl}
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.TOP_LEVEL_EXPORT_BY_PKG_KEY
import org.jetbrains.plugins.scala.lang.psi.stubs.{ScExportStmtStub, ScImportStmtStub}

abstract sealed class ScImportOrExportStmtElementType[P <: ScImportOrExportStmt](debugName: String)
  extends ScStubElementType[P](debugName)

final class ScImportStmtElementType extends ScImportOrExportStmtElementType[ScImportStmt]("ScImportStatement") {
  override def createElement(node: ASTNode): ScImportStmt = new ScImportStmtImpl(null, null, node, toString)
}

final class ScImportStmtStubFactory(elementType: ScImportStmtElementType)
  extends ScStubSerializingElementFactory[ScImportStmtStub, ScImportStmt](elementType) {

  override def createPsi(stub: ScImportStmtStub): ScImportStmt =
    new ScImportStmtImpl(stub, elementType, null, elementType.toString)

  override def createStubImpl(statement: ScImportStmt, parentStub: StubElement[_ <: PsiElement]): ScImportStmtStub =
    new ScImportStmtStubImpl(parentStub, elementType, importText = statement.getText)

  override def serialize(stub: ScImportStmtStub, dataStream: StubOutputStream): Unit =
    dataStream.writeName(stub.importText)

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScImportStmtStub =
    new ScImportStmtStubImpl(parentStub, elementType, importText = dataStream.readNameString)
}

final class ScExportStmtElementType extends ScImportOrExportStmtElementType[ScExportStmt]("ScExportStatement") {
  override def createElement(node: ASTNode): ScExportStmt = new ScExportStmtImpl(null, null, node, toString)
}

final class ScExportStmtStubFactory(elementType: ScExportStmtElementType)
  extends ScStubSerializingElementFactory[ScExportStmtStub, ScExportStmt](elementType) {

  override def createStubImpl(statement: ScExportStmt, parentStub: StubElement[_ <: PsiElement]): ScExportStmtStub =
    new ScExportStmtStubImpl(
      parentStub,
      elementType,
      importText        = statement.getText,
      isTopLevel        = statement.isTopLevel,
      topLevelQualifier = statement.topLevelQualifier
    )

  override def createPsi(stub: ScExportStmtStub): ScExportStmt =
    new ScExportStmtImpl(stub, elementType, null, elementType.toString)

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
}
