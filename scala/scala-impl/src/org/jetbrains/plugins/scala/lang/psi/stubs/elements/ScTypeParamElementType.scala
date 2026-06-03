package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScTypeParamImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTypeParamStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTypeParamStubImpl

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
  }

  override def deserialize(dataStream: StubInputStream,
                           parentStub: StubElement[_ <: PsiElement]) = new ScTypeParamStubImpl(
    parentStub,
    this,
    name = dataStream.readNameString,
    text = dataStream.readNameString,
    lowerBoundText = dataStream.readOptionName,
    upperBoundText = dataStream.readOptionName,
    viewBoundsTexts = dataStream.readNames,
    contextBoundsTexts = dataStream.readNames,
    isCovariant = dataStream.readBoolean,
    isContravariant = dataStream.readBoolean,
    containingFileName = dataStream.readNameString(),
  )

  override def createStubImpl(typeParam: ScTypeParam, parentStub: StubElement[_ <: PsiElement]): ScTypeParamStub = {
    val lowerBoundText = typeParam.lowerTypeElement
      .map(_.getText)
    val upperBoundText = typeParam.upperTypeElement
      .map(_.getText)

    new ScTypeParamStubImpl(
      parentStub,
      this,
      name = typeParam.name,
      text = typeParam.getText,
      lowerBoundText = lowerBoundText,
      upperBoundText = upperBoundText,
      viewBoundsTexts = typeParam.viewTypeElement.asStrings(),
      contextBoundsTexts = typeParam.contextBounds.asStrings(),
      isCovariant = typeParam.isCovariant,
      isContravariant = typeParam.isContravariant,
      containingFileName = typeParam.getContainingFileName,
    )
  }

  override def createElement(node: ASTNode) = new ScTypeParamImpl(node)

  override def createPsi(stub: ScTypeParamStub) = new ScTypeParamImpl(stub)
}