package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import api.statements.{ScPatternDefinition, ScValue, ScValueDeclaration}
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import com.intellij.util.io.StringRef
import impl.ScValueStubImpl
import index.ScalaIndexKeys

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
    val isImplicit = psi.hasModifierProperty("implicit")
    new ScValueStubImpl[ParentPsi](parentStub, this,
      (for (elem <- psi.declaredElements) yield elem.getName).toArray, isDecl, typeText, bodyText, containerText,
      isImplicit)
  }

  def serialize(stub: ScValueStub, dataStream: StubOutputStream) {
    dataStream.writeBoolean(stub.isDeclaration)
    val names = stub.getNames
    dataStream.writeInt(names.length)
    for (name <- names) dataStream.writeName(name)
    dataStream.writeName(stub.getTypeText)
    dataStream.writeName(stub.getBodyText)
    dataStream.writeName(stub.getBindingsContainerText)
    dataStream.writeBoolean(stub.isImplicit)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScValueStub = {
    val isDecl = dataStream.readBoolean
    val namesLength = dataStream.readInt
    val names = new Array[StringRef](namesLength)
    for (i <- 0 until namesLength) names(i) = dataStream.readName
    val parent = parentStub.asInstanceOf[StubElement[PsiElement]]
    val typeText = dataStream.readName
    val bodyText = dataStream.readName
    val bindingsText = dataStream.readName
    val isImplicit = dataStream.readBoolean()
    new ScValueStubImpl(parent, this, names, isDecl, typeText, bodyText, bindingsText, isImplicit)
  }

  def indexStub(stub: ScValueStub, sink: IndexSink) {
    val names = stub.getNames
    
    for (name <- names if name != null) {
      sink.occurrence(ScalaIndexKeys.VALUE_NAME_KEY, name)
    }
    if (stub.isImplicit) sink.occurrence(ScalaIndexKeys.IMPLICITS_KEY, "implicit")
  }
}