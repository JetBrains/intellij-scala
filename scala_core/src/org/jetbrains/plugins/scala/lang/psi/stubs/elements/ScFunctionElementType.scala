package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionDeclarationImpl
import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionDefinitionImpl
import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionImpl
import api.statements.{ScFunction, ScFunctionDeclaration}
import api.toplevel.typedef.ScTemplateDefinition
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
    
    new ScFunctionStubImpl[ParentPsi](parentStub, this, psi.getName, psi.isInstanceOf[ScFunctionDeclaration],
      psi.annotationNames)
  }

  def serialize(stub: ScFunctionStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeBoolean(stub.isDeclaration)
    val annotations = stub.getAnnotations
    dataStream.writeByte(annotations.length)
    for (annotation <- annotations) {
      dataStream.writeName(annotation)
    }
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScFunctionStub = {
    val name = StringRef.toString(dataStream.readName)
    val isDecl = dataStream.readBoolean
    val length = dataStream.readByte
    val annotations = new Array[String](length)
    for (i <- 0 to length - 1) {
      annotations(i) = dataStream.readName.toString
    }
    val parent = parentStub.asInstanceOf[StubElement[PsiElement]]
    new ScFunctionStubImpl(parent, this, name, isDecl, annotations)
  }

  def indexStub(stub: ScFunctionStub, sink: IndexSink): Unit = {

    val name = stub.getName
    if (name != null) {
      sink.occurrence(ScalaIndexKeys.METHOD_NAME_KEY, name)
    }
    for (an <- stub.getAnnotations) {
      sink.occurrence(ScalaIndexKeys.ANNOTATED_MEMBER_KEY, an)
    }

  }
}