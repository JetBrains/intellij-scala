package org.jetbrains.plugins.scala.lang.psi.stubs.elements


import api.toplevel.imports.{ScImportExpr, ScImportSelector}
import com.intellij.util.io.StringRef
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
    dataStream.writeName(stub.asInstanceOf[ScImportExprStubImpl[_]].referenceText.toString)
    dataStream.writeBoolean(stub.isSingleWildcard)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScImportExpr, parentStub: StubElement[ParentPsi]): ScImportExprStub = {
    val refText = psi.reference match {
      case Some(psi) => psi.getText
      case _ => ""
    }
    val singleW = psi.singleWildcard
    new ScImportExprStubImpl(parentStub, this, refText, singleW)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScImportExprStub = {
    val refText: String = StringRef.toString(dataStream.readName)
    val singleW: Boolean = dataStream.readBoolean
    new ScImportExprStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, refText, singleW)
  }

  def indexStub(stub: ScImportExprStub, sink: IndexSink): Unit = {}

  def createPsi(stub: ScImportExprStub): ScImportExpr = {
    new ScImportExprImpl(stub)
  }
}