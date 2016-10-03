package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScVariable, ScVariableDeclaration, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScVariableStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.cleanFqn

/**
  * User: Alexander Podkhalyuzin
  * Date: 18.10.2008
  */

abstract class ScVariableElementType[Variable <: ScVariable](debugName: String)
  extends ScStubElementType[ScVariableStub, ScVariable](debugName) {
  override def serialize(stub: ScVariableStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.isDeclaration)
    dataStream.writeNames(stub.names)
    dataStream.writeOptionName(stub.typeText)
    dataStream.writeOptionName(stub.bodyText)
    dataStream.writeOptionName(stub.bindingsContainerText)
    dataStream.writeBoolean(stub.isLocal)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScVariableStub =
    new ScVariableStubImpl(parentStub, this,
      isDeclaration = dataStream.readBoolean,
      namesRefs = dataStream.readNames,
      typeTextRef = dataStream.readOptionName,
      bodyTextRef = dataStream.readOptionName,
      containerTextRef = dataStream.readOptionName,
      isLocal = dataStream.readBoolean)

  override def createStub(variable: ScVariable, parentStub: StubElement[_ <: PsiElement]): ScVariableStub = {
    val names = variable.declaredElements.map {
      _.name
    }.toArray

    val typeText = variable.typeElement.map {
      _.getText
    }
    val bodyText = Option(variable).collect {
      case definition: ScVariableDefinition => definition
    }.flatMap {
      _.expr
    }.map {
      _.getText
    }

    val containerText = Option(variable).collect {
      case declaration: ScVariableDeclaration => declaration.getIdList
      case definition: ScVariableDefinition => definition.pList
    }.map {
      _.getText
    }

    new ScVariableStubImpl(parentStub, this,
      isDeclaration = variable.isInstanceOf[ScVariableDeclaration],
      namesRefs = names.asReferences,
      typeTextRef = typeText.asReference,
      bodyTextRef = bodyText.asReference,
      containerTextRef = containerText.asReference,
      variable.containingClass == null)
  }

  override def indexStub(stub: ScVariableStub, sink: IndexSink): Unit = {
    stub.names.filter {
      case null => false
      case _ => true
    }.map {
      cleanFqn
    }.foreach {
      sink.occurrence(ScalaIndexKeys.VARIABLE_NAME_KEY, _)
    }
  }
}