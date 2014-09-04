package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements
package signatures

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScParameterStubImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.10.2008
 */

abstract class ScParamElementType[Param <: ScParameter](debugName: String)
extends ScStubElementType[ScParameterStub, ScParameter](debugName) {
  def createStubImpl[ParentPsi <: PsiElement](psi: ScParameter, parentStub: StubElement[ParentPsi]): ScParameterStub = {
    val typeText: String = psi.typeElement match {
      case Some(t) => t.getText
      case None => ""
    }
    val (isVal, isVar) = psi match {
      case c: ScClassParameter => (c.isVal, c.isVar)
      case _ => (false, false)
    }
    val isCallByName = psi.isCallByNameParameter
    val defaultExprText = psi.getActualDefaultExpression.map(_.getText)
    val deprecatedName = psi.deprecatedName
    new ScParameterStubImpl[ParentPsi](parentStub, this, psi.name, typeText, psi.isStable, psi.baseDefaultParam,
      psi.isRepeatedParameter, isVal, isVar, isCallByName, defaultExprText, deprecatedName)
  }

  def serialize(stub: ScParameterStub, dataStream: StubOutputStream) {
    dataStream.writeName(stub.getName)
    dataStream.writeName(stub.getTypeText)
    dataStream.writeBoolean(stub.isStable)
    dataStream.writeBoolean(stub.isDefaultParam)
    dataStream.writeBoolean(stub.isRepeated)
    dataStream.writeBoolean(stub.isVal)
    dataStream.writeBoolean(stub.isVar)
    dataStream.writeBoolean(stub.isCallByNameParameter)
    stub.getDefaultExprText match {
      case None =>
        dataStream.writeBoolean(false)
      case Some(str) =>
        dataStream.writeBoolean(true)
        dataStream.writeName(str)
    }
    stub.deprecatedName match {
      case None => dataStream.writeBoolean(false)
      case Some(name) =>
        dataStream.writeBoolean(true)
        dataStream.writeName(name)
    }
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScParameterStub = {
    val name = dataStream.readName
    val parent = parentStub.asInstanceOf[StubElement[PsiElement]]
    val typeText = dataStream.readName
    val stable = dataStream.readBoolean
    val default = dataStream.readBoolean
    val repeated = dataStream.readBoolean
    val isVal = dataStream.readBoolean
    val isVar = dataStream.readBoolean
    val isCallByName = dataStream.readBoolean()
    val defaultExpr = if (dataStream.readBoolean()) Some(dataStream.readName().toString) else None
    val deprecatedName = if (dataStream.readBoolean()) Some(dataStream.readName().toString) else None
    new ScParameterStubImpl(parent, this, name, typeText, stable, default, repeated, isVal, isVar, isCallByName,
      defaultExpr, deprecatedName)
  }

  def indexStub(stub: ScParameterStub, sink: IndexSink) {}
}