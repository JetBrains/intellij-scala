package org.jetbrains.plugins.scala.lang.psi.stubs.factories

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports.ScImportStmtImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScImportStmtStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScImportStmtElementType
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScImportStmtStubImpl

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
