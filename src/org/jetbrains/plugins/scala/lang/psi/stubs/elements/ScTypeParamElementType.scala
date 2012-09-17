package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements


import api.statements.params.ScTypeParam
import psi.impl.statements.params.ScTypeParamImpl
import com.intellij.psi.stubs.{StubOutputStream, IndexSink, StubElement, StubInputStream}
import com.intellij.psi.PsiElement
import impl.ScTypeParamStubImpl
import com.intellij.util.io._

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

class ScTypeParamElementType[Func <: ScTypeParam]
        extends ScStubElementType[ScTypeParamStub, ScTypeParam]("type parameter") {
  def serialize(stub: ScTypeParamStub, dataStream: StubOutputStream) {
    dataStream.writeName(stub.getName)
    dataStream.writeName(stub.getUpperText)
    dataStream.writeName(stub.getLowerText)
    dataStream.writeBoolean(stub.isContravariant)
    dataStream.writeBoolean(stub.isCovariant)
    dataStream.writeInt(stub.getPositionInFile)
    serialiseArray(dataStream, stub.getViewText)
    serialiseArray(dataStream, stub.getContextBoundText)
    dataStream.writeName(stub.getContainingFileName)
    dataStream.writeName(stub.typeParameterText)
  }

  def indexStub(stub: ScTypeParamStub, sink: IndexSink) {}

  def createPsi(stub: ScTypeParamStub): ScTypeParam = {
    new ScTypeParamImpl(stub)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScTypeParam, parentStub: StubElement[ParentPsi]): ScTypeParamStub = {
    val upperText = psi.upperTypeElement match {
      case Some(te) => te.getText
      case None => ""
    }
    val lowerText = psi.lowerTypeElement match {
      case Some(te) => te.getText
      case None => ""
    }
    val viewText = psi.viewTypeElement.toArray.map(_.getText)
    val contextText = psi.contextBoundTypeElement.toArray.map(_.getText)
    val typeParameterText = psi.getText
    new ScTypeParamStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, psi.name,
      upperText, lowerText, viewText, contextText, psi.isCovariant, psi.isContravariant,
      psi.getTextRange.getStartOffset, psi.getContainingFileName, typeParameterText)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScTypeParamStub = {
    val name = dataStream.readName
    val upperText = dataStream.readName
    val lowerText = dataStream.readName
    val contravariant = dataStream.readBoolean
    val covariant = dataStream.readBoolean
    val position = dataStream.readInt
    val viewText = deserialiseArray(dataStream)
    val contextBoundText = deserialiseArray(dataStream)
    val fileName = dataStream.readName()
    val typeParameterText = dataStream.readName()
    new ScTypeParamStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, name,
      upperText, lowerText, viewText, contextBoundText, covariant, contravariant, position, fileName,
      typeParameterText)
  }

  def deserialiseArray(dataStream: StubInputStream): Array[StringRef] = {
    val n = dataStream.readInt
    val refs = new Array[StringRef](n)
    for (i <- 0 until n) refs(i) = dataStream.readName
    refs
  }

  def serialiseArray(dataStream: StubOutputStream, ref: Array[String]) {
    dataStream.writeInt(ref.size)
    for (r <- ref) dataStream.writeName(r)
  }
}