package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackageContainer
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScPackageContainerStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
  * @author ilyas
  */
abstract class ScPackageContainerElementType[TypeDef <: ScPackageContainer](debugName: String)
  extends ScStubElementType[ScPackageContainerStub, ScPackageContainer](debugName) {
  override def serialize(stub: ScPackageContainerStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.prefix)
    dataStream.writeName(stub.ownNamePart)
    dataStream.writeBoolean(stub.isExplicit)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScPackageContainerStub =
    new ScPackageContainerStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this,
      dataStream.readName, dataStream.readName, dataStream.readBoolean)

  override def createStub(psi: ScPackageContainer, parentStub: StubElement[_ <: PsiElement]): ScPackageContainerStub =
    new ScPackageContainerStubImpl(parentStub, this,
      psi.prefix, psi.ownNamePart, psi.isExplicit)

  override def indexStub(stub: ScPackageContainerStub, sink: IndexSink): Unit = {
    val prefix = stub.prefix
    var ownNamePart = stub.ownNamePart
    def append(postfix: String) =
      ScalaNamesUtil.cleanFqn(if (prefix.length > 0) prefix + "." + postfix else postfix)

    var i = 0
    do {
      sink.occurrence[ScPackageContainer, java.lang.Integer](ScalaIndexKeys.PACKAGE_FQN_KEY, append(ownNamePart).hashCode)
      i = ownNamePart.lastIndexOf(".")
      if (i > 0) {
        ownNamePart = ownNamePart.substring(0, i)
      }
    } while (i > 0)
  }
}