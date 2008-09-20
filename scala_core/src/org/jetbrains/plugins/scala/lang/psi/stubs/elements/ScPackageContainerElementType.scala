package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import _root_.org.jetbrains.plugins.scala.lang.psi.stubs.index.ScFullPackagingNameIndex
import _root_.scala.collection.mutable.ListBuffer
import api.toplevel.packaging.ScPackageContainer
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import com.intellij.util.io.StringRef
import impl.ScPackageContainerStubImpl

/**
 * @author ilyas
 */

abstract class ScPackageContainerElementType[TypeDef <: ScPackageContainer](debugName: String)
extends ScStubElementType[ScPackageContainerStub, ScPackageContainer](debugName) {

  override def createStubImpl[ParentPsi <: PsiElement](psi: ScPackageContainer, 
                                                      parent: StubElement[ParentPsi]): ScPackageContainerStub = {
    new ScPackageContainerStubImpl[ParentPsi](parent, this, psi.fqn)
  }

  def serialize(stub: ScPackageContainerStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.fqn)
  }

  override def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScPackageContainerStub = {
    val qualName = StringRef.toString(dataStream.readName)
    val parent = parentStub.asInstanceOf[StubElement[PsiElement]]
    new ScPackageContainerStubImpl(parent, this, qualName)
  }

  def indexStub(stub: ScPackageContainerStub, sink: IndexSink) = {
    val fqn = stub.fqn
    if (fqn != null && fqn.length > 0) {
      val hd :: tl = List.fromArray(fqn.split("\\."))
      val acc = new ListBuffer[String]()
      acc += hd
      tl.foldLeft(hd)((x, y) => {val z = x + "." + y; acc += z; z})
      for (fq <- acc) {
        sink.occurrence(ScFullPackagingNameIndex.KEY, fq.hashCode)
      }
    }
  }


}