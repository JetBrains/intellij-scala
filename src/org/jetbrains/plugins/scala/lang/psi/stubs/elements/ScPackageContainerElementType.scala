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

/**
 * @author ilyas
 */

abstract class ScPackageContainerElementType[TypeDef <: ScPackageContainer](debugName: String)
extends ScStubElementType[ScPackageContainerStub, ScPackageContainer](debugName) {

  override def createStubImpl[ParentPsi <: PsiElement](psi: ScPackageContainer, 
                                                      parent: StubElement[ParentPsi]): ScPackageContainerStub = {
    new ScPackageContainerStubImpl[ParentPsi](parent, this, psi.prefix, psi.ownNamePart, psi.isExplicit)
  }

  def serialize(stub: ScPackageContainerStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.prefix)
    dataStream.writeName(stub.ownNamePart)
    dataStream.writeBoolean(stub.isExplicit)
  }

  override def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScPackageContainerStub = {
    val prefix = dataStream.readName
    val ownNamePart = dataStream.readName
    val isExplicit = dataStream.readBoolean()
    new ScPackageContainerStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, prefix, ownNamePart, isExplicit)
  }

  def indexStub(stub: ScPackageContainerStub, sink: IndexSink) = {
    val prefix = stub.prefix
    var ownNamePart = stub.ownNamePart
    def append(postfix : String) = if (prefix.length > 0) prefix + "." + postfix else postfix
    var i = 0
    do {
      sink.occurrence[ScPackageContainer, java.lang.Integer](ScalaIndexKeys.PACKAGE_FQN_KEY, append(ownNamePart).hashCode)
      i = ownNamePart.lastIndexOf(".")
      if (i > 0) {
        ownNamePart = ownNamePart.substring(0, i)
      }
    } while(i > 0)
  }
}