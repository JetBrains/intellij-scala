package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports.ScImportExprImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScImportExprStubImpl

/**
  * User: Alexander Podkhalyuzin
  * Date: 20.06.2009
  */
class ScImportExprElementType extends ScStubElementType[ScImportExprStub, ScImportExpr]("import expression") {
  override def serialize(stub: ScImportExprStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeOptionName(stub.referenceText)
    dataStream.writeBoolean(stub.isSingleWildcard)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScImportExprStub =
    new ScImportExprStubImpl(parentStub, this,
      referenceTextRef = dataStream.readOptionName,
      isSingleWildcard = dataStream.readBoolean)

  override def createStub(expr: ScImportExpr, parentStub: StubElement[_ <: PsiElement]): ScImportExprStub = {
    val referenceText = expr.reference.map {
      _.getText
    }

    new ScImportExprStubImpl(parentStub, this,
      referenceTextRef = referenceText.asReference,
      isSingleWildcard = expr.isSingleWildcard)
  }

  override def createPsi(stub: ScImportExprStub): ScImportExpr = new ScImportExprImpl(stub)

  override def createElement(node: ASTNode): ScImportExpr = new ScImportExprImpl(node)
}