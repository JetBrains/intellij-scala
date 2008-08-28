package org.jetbrains.plugins.scala.lang.psi.stubs.elements
import index.{ScShortClassNameIndex, ScFullClassNameIndex}
import com.intellij.util.io.StringRef
import impl.ScTypeDefinitionStubImpl
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import api.toplevel.typedef.ScTypeDefinition

/**
 * @author ilyas
 */

abstract class ScTypeDefinitionElementType[TypeDef <: ScTypeDefinition](debugName: String)
extends ScStubElementType[ScTypeDefinitionStub, ScTypeDefinition](debugName) {

  override def createStubImpl(psi: ScTypeDefinition, parent: Any): ScTypeDefinitionStub = {
    val parentStub = parent.asInstanceOf[StubElement[PsiElement]]
    new ScTypeDefinitionStubImpl(parentStub, this, psi.getName, psi.getQualifiedName, null)
  }

  def serialize(stub: ScTypeDefinitionStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeName(stub.qualName)
  }

  override def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScTypeDefinitionStub = {
    val name = StringRef.toString(dataStream.readName)
    val qualName = StringRef.toString(dataStream.readName)
    val parent = parentStub.asInstanceOf[StubElement[PsiElement]]
    new ScTypeDefinitionStubImpl(parent, this, name, qualName, null)
  }

  def indexStub(stub: ScTypeDefinitionStub, sink: IndexSink) = {
    val name = stub.getName
    if (name != null) {
      sink.occurrence(ScShortClassNameIndex.KEY, name)
    }
    val fqn = stub.qualName
    if (fqn != null) {
      sink.occurrence(ScFullClassNameIndex.KEY, fqn.hashCode)
    }
  }
}