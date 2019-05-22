package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements
package signatures

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScParameterStubImpl

/**
  * User: Alexander Podkhalyuzin
  * Date: 19.10.2008
  */
abstract class ScParamElementType[P <: ScParameter](debugName: String) extends ScStubElementType[ScParameterStub, ScParameter](debugName) {

  override def serialize(stub: ScParameterStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeOptionName(stub.typeText)
    dataStream.writeBoolean(stub.isStable)
    dataStream.writeBoolean(stub.isDefaultParameter)
    dataStream.writeBoolean(stub.isRepeated)
    dataStream.writeBoolean(stub.isVal)
    dataStream.writeBoolean(stub.isVar)
    dataStream.writeBoolean(stub.isCallByNameParameter)
    dataStream.writeOptionName(stub.bodyText)
    dataStream.writeOptionName(stub.deprecatedName)
    dataStream.writeOptionName(stub.implicitType)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScParameterStub =
    new ScParameterStubImpl(parentStub, this,
      name = dataStream.readNameString,
      typeText = dataStream.readOptionName,
      isStable = dataStream.readBoolean,
      isDefaultParameter = dataStream.readBoolean,
      isRepeated = dataStream.readBoolean,
      isVal = dataStream.readBoolean,
      isVar = dataStream.readBoolean,
      isCallByNameParameter = dataStream.readBoolean,
      bodyText = dataStream.readOptionName,
      deprecatedName = dataStream.readOptionName,
      implicitType = dataStream.readOptionName)

  override def createStubImpl(parameter: ScParameter, parentStub: StubElement[_ <: PsiElement]): ScParameterStub = {
    val typeText = parameter.typeElement.map {
      _.getText
    }
    val (isVal, isVar, implicitType) = parameter match {
      case parameter: ScClassParameter =>
        (parameter.isVal, parameter.isVar, ScImplicitInstanceStub.implicitType(parameter, parameter.typeElement))
      case _ => (false, false, None)
    }
    val defaultExprText = parameter.getActualDefaultExpression.map {
      _.getText
    }

    new ScParameterStubImpl(parentStub, this,
      name = parameter.name,
      typeText = typeText,
      isStable = parameter.isStable,
      isDefaultParameter = parameter.baseDefaultParam,
      isRepeated = parameter.isRepeatedParameter,
      isVal = isVal,
      isVar = isVar,
      isCallByNameParameter = parameter.isCallByNameParameter,
      bodyText = defaultExprText,
      deprecatedName = parameter.deprecatedName,
      implicitType = implicitType)
  }
}