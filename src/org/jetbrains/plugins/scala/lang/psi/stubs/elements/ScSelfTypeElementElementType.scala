package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements


import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
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
  def serialize(stub: ScSelfTypeElementStub, dataStream: StubOutputStream) {
    dataStream.writeName(stub.getName)
    dataStream.writeName(stub.getTypeElementText)
    val names = stub.getClassNames
    dataStream.writeInt(names.length)
    names.foreach(dataStream.writeName(_))
  }

  def createPsi(stub: ScSelfTypeElementStub): ScSelfTypeElement = {
    new ScSelfTypeElementImpl(stub)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScSelfTypeElement, parentStub: StubElement[ParentPsi]): ScSelfTypeElementStub = {
    new ScSelfTypeElementStubImpl(parentStub, this, psi.name, psi.typeElement match {case None => "" case Some(x) => x.getText},
      psi.getClassNames)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScSelfTypeElementStub = {
    val name = dataStream.readName
    val typeElementText = dataStream.readName
    val n = dataStream.readInt()
    val names = 1.to(n).map(_ => dataStream.readName().toString).toArray
    new ScSelfTypeElementStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, name.toString,
      typeElementText.toString, names)
  }

  def indexStub(stub: ScSelfTypeElementStub, sink: IndexSink) {
    for (name <- stub.getClassNames) {
      sink.occurrence(ScSelfTypeInheritorsIndex.KEY, name)
    }
  }
}