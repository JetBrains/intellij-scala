package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScTypeParamImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTypeParamStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTypeParamStubImpl

class ScTypeParamElementType extends ScalaStubBasedElementType[ScTypeParamStub, ScTypeParam](ScTypeParamElementType.DebugName) {
  override def createElement(node: ASTNode): ScTypeParam = new ScTypeParamImpl(node)
}

object ScTypeParamElementType {
  val DebugName = "type parameter"
}

class ScTypeParamStubFactory(elementType: IElementType)
  extends StubSerializingElementFactory[ScTypeParamStub, ScTypeParam] {

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

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScTypeParamStub = new ScTypeParamStubImpl(
    parentStub,
    elementType,
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

  override def createStub(typeParam: ScTypeParam, parentStub: StubElement[_ <: PsiElement]): ScTypeParamStub =
    ScStubElementType.Processing.run {
      val lowerBoundText = typeParam.lowerTypeElement
        .map(_.getText)
      val upperBoundText = typeParam.upperTypeElement
        .map(_.getText)

      new ScTypeParamStubImpl(
        parentStub,
        elementType,
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

  override def createPsi(stub: ScTypeParamStub): ScTypeParam = new ScTypeParamImpl(stub)

  override def indexStub(stub: ScTypeParamStub, sink: IndexSink): Unit = {}

  override def getExternalId: String = s"scala.${ScTypeParamElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
