package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements


import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import com.intellij.util.io._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScTypeParamImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTypeParamStubImpl

import scala.collection.mutable.ArrayBuffer

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
    serialiseSeq(dataStream, stub.getViewText)
    serialiseSeq(dataStream, stub.getContextBoundText)
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
    val viewText = psi.viewTypeElement.map(te => StringRef.fromString(te.getText))
    val contextText = psi.contextBoundTypeElement.map(te => StringRef.fromString(te.getText))
    val typeParameterText = psi.getText
    new ScTypeParamStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, StringRef.fromString(psi.name),
      StringRef.fromString(upperText), StringRef.fromString(lowerText), viewText, contextText, psi.isCovariant, psi.isContravariant,
      psi.getTextRange.getStartOffset, StringRef.fromString(psi.getContainingFileName), StringRef.fromString(typeParameterText))
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScTypeParamStub = {
    val name = dataStream.readName
    val upperText = dataStream.readName
    val lowerText = dataStream.readName
    val contravariant = dataStream.readBoolean
    val covariant = dataStream.readBoolean
    val position = dataStream.readInt
    val viewText = deserialiseSeq(dataStream)
    val contextBoundText = deserialiseSeq(dataStream)
    val fileName = dataStream.readName()
    val typeParameterText = dataStream.readName()
    new ScTypeParamStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, name,
      upperText, lowerText, viewText, contextBoundText, covariant, contravariant, position, fileName,
      typeParameterText)
  }

  def deserialiseSeq(dataStream: StubInputStream): Seq[StringRef] = {
    val n = dataStream.readInt
    val refs = new ArrayBuffer[StringRef]
    for (i <- 0 until n) refs += dataStream.readName
    refs
  }

  def serialiseSeq(dataStream: StubOutputStream, ref: Seq[String]) {
    dataStream.writeInt(ref.length)
    for (r <- ref) dataStream.writeName(r)
  }
}