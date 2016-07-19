package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.extensions.MaybePsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScSelfTypeElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScSelfTypeElementStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScSelfTypeInheritorsIndex

/**
  * User: Alexander Podkhalyuzin
  * Date: 19.06.2009
  */
class ScSelfTypeElementElementType[Func <: ScSelfTypeElement]
  extends ScStubElementType[ScSelfTypeElementStub, ScSelfTypeElement]("self type element") {
  override def serialize(stub: ScSelfTypeElementStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeName(stub.getTypeElementText)
    val names = stub.getClassNames
    dataStream.writeInt(names.length)
    names.foreach(dataStream.writeName)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScSelfTypeElementStub = {
    val name = dataStream.readName
    val typeElementText = dataStream.readName
    val n = dataStream.readInt()
    val names = 1.to(n).map(_ => dataStream.readName().toString).toArray
    new ScSelfTypeElementStubImpl(parentStub, this,
      name.toString, typeElementText.toString, names)
  }

  override def createStub(psi: ScSelfTypeElement, parentStub: StubElement[_ <: PsiElement]): ScSelfTypeElementStub =
    new ScSelfTypeElementStubImpl(parentStub, this,
      psi.name, psi.typeElement.text, psi.getClassNames)

  override def indexStub(stub: ScSelfTypeElementStub, sink: IndexSink): Unit = {
    for (name <- stub.getClassNames) {
      sink.occurrence(ScSelfTypeInheritorsIndex.KEY, name)
    }
  }

  override def createElement(node: ASTNode): ScSelfTypeElement = new ScSelfTypeElementImpl(node)

  override def createPsi(stub: ScSelfTypeElementStub): ScSelfTypeElement = new ScSelfTypeElementImpl(stub)
}