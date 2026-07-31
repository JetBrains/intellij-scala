package org.jetbrains.plugins.scala.lang.psi.stubs.factories

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScExportStmt
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports.ScExportStmtImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScExportStmtStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScExportStmtElementType
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScExportStmtStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.TOP_LEVEL_EXPORT_BY_PKG_KEY

final class ScExportStmtStubFactory(elementType: ScExportStmtElementType)
  extends ScStubSerializingElementFactory[ScExportStmtStub, ScExportStmt](elementType) {

  override def createStubImpl(statement: ScExportStmt, parentStub: StubElement[_ <: PsiElement]): ScExportStmtStub =
    new ScExportStmtStubImpl(
      parentStub,
      elementType,
      importText = statement.getText,
      isTopLevel = statement.isTopLevel,
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
      importText = dataStream.readNameString,
      isTopLevel = dataStream.readBoolean,
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
