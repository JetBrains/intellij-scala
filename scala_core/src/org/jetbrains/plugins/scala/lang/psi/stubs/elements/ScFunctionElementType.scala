package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionDeclarationImpl
import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionDefinitionImpl
import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionImpl
import api.statements.{ScFunction, ScFunctionDeclaration}
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import com.intellij.util.io.StringRef
import impl.ScFunctionStubImpl
import index.ScalaIndexKeys

/**
 * User: Alexander Podkhalyuzin
 * Date: 14.10.2008
 */

abstract class ScFunctionElementType[Func <: ScFunction](debugName: String)
extends ScStubElementType[ScFunctionStub, ScFunction](debugName) {
  def createStubImpl[ParentPsi <: PsiElement](psi: ScFunction, parentStub: StubElement[ParentPsi]): ScFunctionStub = {
    new ScFunctionStubImpl[ParentPsi](parentStub, this, psi.getName, psi.isInstanceOf[ScFunctionDeclaration])
  }

  def serialize(stub: ScFunctionStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeBoolean(stub.isDeclaration)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScFunctionStub = {
    val name = StringRef.toString(dataStream.readName)
    val isDecl = dataStream.readBoolean
    val parent = parentStub.asInstanceOf[StubElement[PsiElement]]
    new ScFunctionStubImpl(parent, this, name, isDecl)
  }

  def indexStub(stub: ScFunctionStub, sink: IndexSink): Unit = {
    val name = stub.getName
    if (name != null) {
      sink.occurrence(ScalaIndexKeys.METHOD_NAME_KEY, name)
    }
  }
}