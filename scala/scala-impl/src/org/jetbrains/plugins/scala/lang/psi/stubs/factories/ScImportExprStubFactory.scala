package org.jetbrains.plugins.scala.lang.psi.stubs.factories

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports.ScImportExprImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScImportExprStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScImportExprElementType
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScImportExprStubImpl

final class ScImportExprStubFactory(elementType: ScImportExprElementType)
  extends ScStubSerializingElementFactory[ScImportExprStub, ScImportExpr](elementType) {

  override def serialize(stub: ScImportExprStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeOptionName(stub.referenceText)
    dataStream.writeBoolean(stub.hasWildcardSelector)
    dataStream.writeBoolean(stub.hasGivenSelector)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScImportExprStub =
    new ScImportExprStubImpl(parentStub, elementType,
      referenceText = dataStream.readOptionName,
      hasWildcardSelector = dataStream.readBoolean,
      hasGivenSelector = dataStream.readBoolean)

  override def createStubImpl(expr: ScImportExpr, parentStub: StubElement[_ <: PsiElement]): ScImportExprStub =
    new ScImportExprStubImpl(parentStub, elementType,
      referenceText = expr.reference.map(_.getText),
      hasWildcardSelector = expr.hasWildcardSelector,
      hasGivenSelector = expr.hasGivenSelector
    )

  override def createPsi(stub: ScImportExprStub): ScImportExpr = new ScImportExprImpl(stub)
}
