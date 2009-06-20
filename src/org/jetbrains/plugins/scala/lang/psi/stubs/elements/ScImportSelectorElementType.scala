package org.jetbrains.plugins.scala.lang.psi.stubs.elements


import api.toplevel.imports.{ScImportSelector, ScImportStmt}
import impl.{ScImportSelectorStubImpl, ScImportStmtStubImpl}
import com.intellij.psi.stubs.{IndexSink, StubInputStream, StubElement, StubOutputStream}

import com.intellij.psi.PsiElement
import psi.impl.toplevel.imports.{ScImportSelectorImpl, ScImportStmtImpl}
/**
 * User: Alexander Podkhalyuzin
 * Date: 20.06.2009
 */

class ScImportSelectorElementType[Func <: ScImportSelector]
        extends ScStubElementType[ScImportSelectorStub, ScImportSelector]("import selector") {
  def serialize(stub: ScImportSelectorStub, dataStream: StubOutputStream): Unit = {
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScImportSelector, parentStub: StubElement[ParentPsi]): ScImportSelectorStub = {
    new ScImportSelectorStubImpl(parentStub, this)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScImportSelectorStub = {
    new ScImportSelectorStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this)
  }

  def indexStub(stub: ScImportSelectorStub, sink: IndexSink): Unit = {}

  def createPsi(stub: ScImportSelectorStub): ScImportSelector = {
    new ScImportSelectorImpl(stub)
  }
}