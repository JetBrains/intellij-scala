package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import com.intellij.util.io._
import org.jetbrains.plugins.scala.extensions.MaybePsiElementExt
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
  override def serialize(stub: ScTypeParamStub, dataStream: StubOutputStream): Unit = {
    def serializeSeq(ref: Seq[String]): Unit = {
      dataStream.writeInt(ref.length)
      for (r <- ref) dataStream.writeName(r)
    }

    dataStream.writeName(stub.getName)
    dataStream.writeName(stub.getUpperText)
    dataStream.writeName(stub.getLowerText)
    dataStream.writeBoolean(stub.isContravariant)
    dataStream.writeBoolean(stub.isCovariant)
    dataStream.writeInt(stub.getPositionInFile)
    serializeSeq(stub.getViewText)
    serializeSeq(stub.getContextBoundText)
    dataStream.writeName(stub.getContainingFileName)
    dataStream.writeName(stub.typeParameterText)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScTypeParamStub = {
    def deserializeSeq(): Seq[StringRef] = {
      val n = dataStream.readInt
      val refs = new ArrayBuffer[StringRef]
      for (i <- 0 until n) refs += dataStream.readName
      refs
    }

    val name = dataStream.readName
    val upperText = dataStream.readName
    val lowerText = dataStream.readName
    val contravariant = dataStream.readBoolean
    val covariant = dataStream.readBoolean
    val position = dataStream.readInt
    val viewText = deserializeSeq()
    val contextBoundText = deserializeSeq()
    val fileName = dataStream.readName()
    val typeParameterText = dataStream.readName()
    new ScTypeParamStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, name,
      upperText, lowerText, viewText, contextBoundText, covariant, contravariant, position, fileName,
      typeParameterText)
  }

  override def createStub(psi: ScTypeParam, parentStub: StubElement[_ <: PsiElement]): ScTypeParamStub = {
    val upperText = psi.upperTypeElement.text
    val lowerText = psi.lowerTypeElement.text
    val viewText = psi.viewTypeElement.map(te => StringRef.fromString(te.getText))
    val contextText = psi.contextBoundTypeElement.map(te => StringRef.fromString(te.getText))
    val typeParameterText = psi.getText
    new ScTypeParamStubImpl(parentStub, this, StringRef.fromString(psi.name),
      StringRef.fromString(upperText), StringRef.fromString(lowerText), viewText, contextText, psi.isCovariant, psi.isContravariant,
      psi.getTextRange.getStartOffset, StringRef.fromString(psi.getContainingFileName), StringRef.fromString(typeParameterText))
  }

  override def createElement(node: ASTNode): ScTypeParam = new ScTypeParamImpl(node)

  override def createPsi(stub: ScTypeParamStub): ScTypeParam = new ScTypeParamImpl(stub)
}