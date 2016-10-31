package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScSelfTypeElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScSelfTypeElementStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScSelfTypeInheritorsIndex.KEY

/**
  * User: Alexander Podkhalyuzin
  * Date: 19.06.2009
  */
class ScSelfTypeElementElementType extends ScStubElementType[ScSelfTypeElementStub, ScSelfTypeElement]("self type element") {

  override def serialize(stub: ScSelfTypeElementStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeOptionName(stub.typeText)
    dataStream.writeNames(stub.classNames)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScSelfTypeElementStub =
    new ScSelfTypeElementStubImpl(parentStub, this,
      nameRef = dataStream.readName,
      typeTextRef = dataStream.readOptionName,
      typeNamesRefs = dataStream.readNames)

  override def createStub(typeElement: ScSelfTypeElement, parentStub: StubElement[_ <: PsiElement]): ScSelfTypeElementStub = {
    val typeElementText = typeElement.typeElement.map {
      _.getText
    }

    new ScSelfTypeElementStubImpl(parentStub, this,
      nameRef = StringRef.fromString(typeElement.name),
      typeTextRef = typeElementText.asReference,
      typeNamesRefs = typeElement.classNames.asReferences)
  }

  override def indexStub(stub: ScSelfTypeElementStub, sink: IndexSink): Unit =
    this.indexStub(stub.classNames, sink, KEY)

  override def createElement(node: ASTNode): ScSelfTypeElement = new ScSelfTypeElementImpl(node)

  override def createPsi(stub: ScSelfTypeElementStub): ScSelfTypeElement = new ScSelfTypeElementImpl(stub)
}