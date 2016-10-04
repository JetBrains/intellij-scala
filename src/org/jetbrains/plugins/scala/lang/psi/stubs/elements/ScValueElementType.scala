package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScValueStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.VALUE_NAME_KEY

/**
  * User: Alexander Podkhalyuzin
  * Date: 17.10.2008
  */
abstract class ScValueElementType[V <: ScValue](debugName: String)
  extends ScValueOrVariableElementType[ScValueStub, ScValue](debugName) {
  override protected val key = VALUE_NAME_KEY

  override def serialize(stub: ScValueStub, dataStream: StubOutputStream): Unit = {
    super.serialize(stub, dataStream)
    dataStream.writeBoolean(stub.isImplicit)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScValueStub =
    new ScValueStubImpl(parentStub, this,
      isDeclaration = dataStream.readBoolean,
      namesRefs = dataStream.readNames,
      typeTextRef = dataStream.readOptionName,
      bodyTextRef = dataStream.readOptionName,
      containerTextRef = dataStream.readOptionName,
      isLocal = dataStream.readBoolean,
      isImplicit = dataStream.readBoolean)

  override def createStub(value: ScValue, parentStub: StubElement[_ <: PsiElement]): ScValueStub =
    new ScValueStubImpl(parentStub, this,
      isDeclaration = isDeclaration(value),
      namesRefs = names(value),
      typeTextRef = typeText(value),
      bodyTextRef = bodyText(value),
      containerTextRef = containerText(value),
      isLocal = isLocal(value),
      isImplicit = value.hasModifierProperty("implicit"))

  override def indexStub(stub: ScValueStub, sink: IndexSink): Unit = {
    super.indexStub(stub, sink)
    if (stub.isImplicit) {
      this.indexImplicit(sink)
    }
  }
}