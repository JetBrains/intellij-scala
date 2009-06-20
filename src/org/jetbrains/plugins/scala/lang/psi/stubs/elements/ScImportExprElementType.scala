package org.jetbrains.plugins.scala.lang.psi.stubs.elements


import api.toplevel.imports.{ScImportExpr, ScImportSelector}
import psi.impl.toplevel.imports.{ScImportExprImpl, ScImportSelectorImpl}
import impl.{ScImportExprStubImpl, ScImportSelectorStubImpl}
import com.intellij.psi.stubs.{IndexSink, StubInputStream, StubElement, StubOutputStream}

import com.intellij.psi.PsiElement
/**
 * User: Alexander Podkhalyuzin
 * Date: 20.06.2009
 */

class ScImportExprElementType[Func <: ScImportExpr]
        extends ScStubElementType[ScImportExprStub, ScImportExpr]("import expression") {
  def serialize(stub: ScImportExprStub, dataStream: StubOutputStream): Unit = {
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScImportExpr, parentStub: StubElement[ParentPsi]): ScImportExprStub = {
    new ScImportExprStubImpl(parentStub, this)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScImportExprStub = {
    new ScImportExprStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this)
  }

  def indexStub(stub: ScImportExprStub, sink: IndexSink): Unit = {}

  def createPsi(stub: ScImportExprStub): ScImportExpr = {
    new ScImportExprImpl(stub)
  }
}