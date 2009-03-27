package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import _root_.scala.collection.mutable.ListBuffer
import api.toplevel.packaging.ScPackageContainer
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import com.intellij.util.io.StringRef
import impl.ScPackageContainerStubImpl
import index.{ScalaIndexKeys, ScFullPackagingNameIndex}

/**
 * @author ilyas
 */

abstract class ScPackageContainerElementType[TypeDef <: ScPackageContainer](debugName: String)
extends ScStubElementType[ScPackageContainerStub, ScPackageContainer](debugName) {

  override def createStubImpl[ParentPsi <: PsiElement](psi: ScPackageContainer, 
                                                      parent: StubElement[ParentPsi]): ScPackageContainerStub = {
    new ScPackageContainerStubImpl[ParentPsi](parent, this, psi.prefix, psi.ownNamePart)
  }

  def serialize(stub: ScPackageContainerStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.prefix)
    dataStream.writeName(stub.ownNamePart)
  }

  override def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScPackageContainerStub = {
    val prefix = StringRef.toString(dataStream.readName)
    val ownNamePart = StringRef.toString(dataStream.readName)
    val parent = parentStub.asInstanceOf[StubElement[PsiElement]]
    new ScPackageContainerStubImpl(parent, this, prefix, ownNamePart)
  }

  def indexStub(stub: ScPackageContainerStub, sink: IndexSink) = {
    val prefix = stub.prefix
    var ownNamePart = stub.ownNamePart
    def append(postfix : String) = if (prefix.length > 0) prefix + "." + postfix else postfix
    var i = 0
    do {
      sink.occurrence(ScalaIndexKeys.PACKAGE_FQN_KEY, append(ownNamePart).hashCode)
      i = ownNamePart.lastIndexOf(".")
      if (i > 0) {
        ownNamePart = ownNamePart.substring(0, i)
      }
    } while(i > 0)
  }
}