package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScVariableStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.VARIABLE_NAME_KEY

/**
  * User: Alexander Podkhalyuzin
  * Date: 18.10.2008
  */
abstract class ScVariableElementType[V <: ScVariable](debugName: String)
  extends ScValueOrVariableElementType[ScVariableStub, ScVariable](debugName) {
  override protected val key = VARIABLE_NAME_KEY

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScVariableStub =
    new ScVariableStubImpl(parentStub, this,
      isDeclaration = dataStream.readBoolean,
      namesRefs = dataStream.readNames,
      typeTextRef = dataStream.readOptionName,
      bodyTextRef = dataStream.readOptionName,
      containerTextRef = dataStream.readOptionName,
      isLocal = dataStream.readBoolean)

  override def createStub(variable: ScVariable, parentStub: StubElement[_ <: PsiElement]): ScVariableStub =
    new ScVariableStubImpl(parentStub, this,
      isDeclaration = isDeclaration(variable),
      namesRefs = names(variable),
      typeTextRef = typeText(variable),
      bodyTextRef = bodyText(variable),
      containerTextRef = containerText(variable),
      isLocal = isLocal(variable))
}