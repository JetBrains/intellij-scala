package org.jetbrains.plugins.scala.lang.psi.stubs.elements

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

  override def createStubImpl(psi: ScPackageContainer, parent: Any): ScPackageContainerStub = {
    val parentStub = parent.asInstanceOf[StubElement[PsiElement]]
    new ScPackageContainerStubImpl(parentStub, this, psi.fqn)
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
    /*todo implement me for all nested packages!
        val name = stub.getName
        if (name != null) {
          sink.occurrence(ScShortClassNameIndex.KEY, name)
        }
        val fqn = stub.qualName
        if (fqn != null) {
          sink.occurrence(ScFullClassNameIndex.KEY, fqn.hashCode)
        }
    */
  }


}