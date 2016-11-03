package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import com.intellij.util.io.StringRef.fromString
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScTypeParamImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTypeParamStubImpl

/**
  * User: Alexander Podkhalyuzin
  * Date: 17.06.2009
  */
class ScTypeParamElementType extends ScStubElementType[ScTypeParamStub, ScTypeParam]("type parameter") {
  override def serialize(stub: ScTypeParamStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeName(stub.text)
    dataStream.writeOptionName(stub.lowerBoundText)
    dataStream.writeOptionName(stub.upperBoundText)
    dataStream.writeNames(stub.viewBoundsTexts)
    dataStream.writeNames(stub.contextBoundsTexts)
    dataStream.writeBoolean(stub.isCovariant)
    dataStream.writeBoolean(stub.isContravariant)
    dataStream.writeName(stub.containingFileName)
    dataStream.writeInt(stub.positionInFile)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScTypeParamStub = {
    new ScTypeParamStubImpl(parentStub, this,
      nameRef = dataStream.readName,
      textRef = dataStream.readName,
      lowerBoundTextRef = dataStream.readOptionName,
      upperBoundTextRef = dataStream.readOptionName,
      viewBoundsTextRefs = dataStream.readNames,
      contextBoundsTextRefs = dataStream.readNames,
      isCovariant = dataStream.readBoolean,
      isContravariant = dataStream.readBoolean,
      containingFileNameRef = dataStream.readName(),
      positionInFile = dataStream.readInt)
  }

  override def createStub(typeParam: ScTypeParam, parentStub: StubElement[_ <: PsiElement]): ScTypeParamStub = {
    val lowerBoundText = typeParam.lowerTypeElement.map {
      _.getText
    }

    val upperBoundText = typeParam.upperTypeElement.map {
      _.getText
    }

    val viewBoundsTexts = typeParam.viewTypeElement.map {
      _.getText
    }.toArray

    val contextBoundsTexts = typeParam.contextBoundTypeElement.map {
      _.getText
    }.toArray

    new ScTypeParamStubImpl(parentStub, this,
      nameRef = fromString(typeParam.name),
      textRef = fromString(typeParam.getText),
      lowerBoundTextRef = lowerBoundText.asReference,
      upperBoundTextRef = upperBoundText.asReference,
      viewBoundsTextRefs = viewBoundsTexts.asReferences,
      contextBoundsTextRefs = contextBoundsTexts.asReferences,
      isCovariant = typeParam.isCovariant,
      isContravariant = typeParam.isContravariant,
      containingFileNameRef = fromString(typeParam.getContainingFileName),
      positionInFile = typeParam.getTextRange.getStartOffset)
  }

  override def createElement(node: ASTNode): ScTypeParam = new ScTypeParamImpl(node)

  override def createPsi(stub: ScTypeParamStub): ScTypeParam = new ScTypeParamImpl(stub)
}