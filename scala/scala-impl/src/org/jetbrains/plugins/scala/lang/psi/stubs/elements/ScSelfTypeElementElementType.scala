package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScSelfTypeElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScSelfTypeElementStubImpl

class ScSelfTypeElementElementType extends ScStubElementType[ScSelfTypeElementStub, ScSelfTypeElement]("self type element") {

  override def serialize(stub: ScSelfTypeElementStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeOptionName(stub.typeText)
    dataStream.writeNames(stub.classNames)
  }

  override def deserialize(dataStream: StubInputStream,
                           parentStub: StubElement[_ <: PsiElement]) = new ScSelfTypeElementStubImpl(
    parentStub,
    this,
    name = dataStream.readNameString,
    typeText = dataStream.readOptionName,
    classNames = dataStream.readNames
  )

  override def createStubImpl(typeElement: ScSelfTypeElement,
                              parentStub: StubElement[_ <: PsiElement]): ScSelfTypeElementStub = {
    val typeElementText = typeElement.typeElement.map(_.getText)

    new ScSelfTypeElementStubImpl(
      parentStub,
      this,
      name = typeElement.name,
      typeText = typeElementText,
      classNames = typeElement.classNames
    )
  }

  override def indexStub(stub: ScSelfTypeElementStub, sink: IndexSink): Unit = {
    sink.occurrences(index.ScalaIndexKeys.SELF_TYPE_CLASS_NAME_KEY, stub.classNames.toSeq: _*)
  }

  override def createElement(node: ASTNode) = new ScSelfTypeElementImpl(node)

  override def createPsi(stub: ScSelfTypeElementStub) = new ScSelfTypeElementImpl(stub)
}