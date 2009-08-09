package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import _root_.scala.collection.mutable.ArrayBuffer
import api.statements.{ScVariableDefinition, ScVariable, ScVariableDeclaration}
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import com.intellij.util.io.StringRef
import impl.ScVariableStubImpl
import index.ScalaIndexKeys
import java.io.IOException

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.10.2008
 */

abstract class ScVariableElementType[Variable <: ScVariable](debugName: String)
extends ScStubElementType[ScVariableStub, ScVariable](debugName) {
  def createStubImpl[ParentPsi <: PsiElement](psi: ScVariable, parentStub: StubElement[ParentPsi]): ScVariableStub = {
    val isDecl = psi.isInstanceOf[ScVariableDeclaration]
    val typeText = psi.typeElement match {
      case Some(te) => te.getText
      case None => ""
    }
    val bodyText = if (!isDecl) psi.asInstanceOf[ScVariableDefinition].expr.getText else ""
    val containerText = if (isDecl) psi.asInstanceOf[ScVariableDeclaration].getIdList.getText
      else psi.asInstanceOf[ScVariableDefinition].pList.getText
    new ScVariableStubImpl[ParentPsi](parentStub, this,
      (for (elem <- psi.declaredElements) yield elem.getName).toArray,
      isDecl, typeText, bodyText, containerText)
  }

  def serialize(stub: ScVariableStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.isDeclaration)
    val names = stub.getNames
    dataStream.writeInt(names.length)
    for (name <- names) dataStream.writeName(name)
    dataStream.writeName(stub.getTypeText)
    dataStream.writeName(stub.getBodyText)
    dataStream.writeName(stub.getBindingsContainerText)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScVariableStub = {
    val isDecl = dataStream.readBoolean
    val namesLength = dataStream.readInt
    val names = new Array[String](namesLength)
    for (i <- 0 to (namesLength - 1)) names(i) = StringRef.toString(dataStream.readName)
    val parent = parentStub.asInstanceOf[StubElement[PsiElement]]
    val typeText = StringRef.toString(dataStream.readName)
    val bodyText = StringRef.toString(dataStream.readName)
    val bindingsText = StringRef.toString(dataStream.readName)
    new ScVariableStubImpl(parent, this, names, isDecl, typeText, bodyText, bindingsText)
  }

  def indexStub(stub: ScVariableStub, sink: IndexSink): Unit = {
    val names = stub.getNames
    for (name <- names if name != null) {
      sink.occurrence(ScalaIndexKeys.VARIABLE_NAME_KEY, name)
    }
  }
}