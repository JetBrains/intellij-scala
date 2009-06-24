package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import _root_.scala.collection.mutable.ArrayBuffer
import api.statements.{ScPatternDefinition, ScValue, ScValueDeclaration}
import api.toplevel.templates.ScTemplateBody
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import com.intellij.util.io.StringRef
import impl.ScValueStubImpl
import index.ScalaIndexKeys
import java.io.IOException

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.10.2008
 */

abstract class ScValueElementType[Value <: ScValue](debugName: String)
extends ScStubElementType[ScValueStub, ScValue](debugName) {
  def createStubImpl[ParentPsi <: PsiElement](psi: ScValue, parentStub: StubElement[ParentPsi]): ScValueStub = {
    val isDecl = psi.isInstanceOf[ScValueDeclaration]
    val typeText = psi.typeElement match {
      case Some(te) => te.getText
      case None => ""
    }
    val bodyText = if (!isDecl) psi.asInstanceOf[ScPatternDefinition].expr.getText else ""
    val containerText = if (isDecl) psi.asInstanceOf[ScValueDeclaration].getIdList.getText
      else psi.asInstanceOf[ScPatternDefinition].pList.getText
    new ScValueStubImpl[ParentPsi](parentStub, this,
      (for (elem <- psi.declaredElements) yield elem.getName).toArray, isDecl, typeText, bodyText, containerText)
  }

  def serialize(stub: ScValueStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.isDeclaration)
    val names = stub.getNames
    dataStream.writeInt(names.length)
    for (name <- names) dataStream.writeName(name)
    dataStream.writeName(stub.getTypeText)
    dataStream.writeName(stub.getBodyText)
    dataStream.writeName(stub.getBindingsContainerText)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScValueStub = {
    val isDecl = dataStream.readBoolean
    val namesLength = dataStream.readInt
    val names = new Array[String](namesLength)
    for (i <- 0 to (namesLength - 1)) names(i) = StringRef.toString(dataStream.readName)
    val parent = parentStub.asInstanceOf[StubElement[PsiElement]]
    val typeText = StringRef.toString(dataStream.readName)
    val bodyText = StringRef.toString(dataStream.readName)
    val bindingsText = StringRef.toString(dataStream.readName)
    new ScValueStubImpl(parent, this, names, isDecl, typeText, bodyText, bindingsText)
  }

  def indexStub(stub: ScValueStub, sink: IndexSink): Unit = {
    val names = stub.getNames
    for (name <- names if name != null) {
      sink.occurrence(ScalaIndexKeys.VALUE_NAME_KEY, name)
    }
  }
}