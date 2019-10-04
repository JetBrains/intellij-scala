package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScImportStmtStubImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.06.2009
 */
abstract class ScImportStmtElementType(debugName: String)
  extends ScStubElementType.Impl[ScImportStmtStub, ScImportStmt](debugName) {

  override final def serialize(stub: ScImportStmtStub,
                               dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.importText)
  }

  override final def deserialize(dataStream: StubInputStream,
                                 parentStub: StubElement[_ <: PsiElement]) = new ScImportStmtStubImpl(
    parentStub,
    this,
    importText = dataStream.readNameString
  )

  override final def createStubImpl(statement: ScImportStmt,
                                    parentStub: StubElement[_ <: PsiElement]) = new ScImportStmtStubImpl(
    parentStub,
    this,
    importText = statement.getText
  )
}