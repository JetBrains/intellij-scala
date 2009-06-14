package org.jetbrains.plugins.scala.lang.psi.stubs.elements
import api.toplevel.typedef.{ScObject, ScTypeDefinition}
import com.intellij.psi.{PsiClass, PsiElement}
import com.intellij.util.io.StringRef
import impl.ScTypeDefinitionStubImpl
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import com.intellij.lang.ASTNode
import index.{ScFullClassNameIndex, ScalaIndexKeys, ScShortClassNameIndex}

/**
 * @author ilyas
 */

abstract class ScTypeDefinitionElementType[TypeDef <: ScTypeDefinition](debugName: String)
extends ScStubElementType[ScTypeDefinitionStub, ScTypeDefinition](debugName) {

  override def createStubImpl[ParentPsi <: PsiElement](psi: ScTypeDefinition, parent: StubElement[ParentPsi]): ScTypeDefinitionStub = {
    val file = psi.getContainingFile
    val fileName = if (file != null && file.getVirtualFile != null) file.getVirtualFile.getName else null
    val signs = psi.functions.map(_.name).toArray
    val isPO = psi.isPackageObject
    new ScTypeDefinitionStubImpl[ParentPsi](parent, this, psi.getName, psi.getQualifiedName, fileName, signs, isPO)
  }

  def serialize(stub: ScTypeDefinitionStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeName(stub.qualName)
    dataStream.writeBoolean(stub.isPackageObject)
    dataStream.writeName(stub.sourceFileName)
    val methodNames = stub.methodNames
    dataStream.writeInt(methodNames.length)
    for (name <- methodNames) dataStream.writeName(name)
  }

  override def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScTypeDefinitionStub = {
    val name = StringRef.toString(dataStream.readName)
    val qualName = StringRef.toString(dataStream.readName)
    val isPO = dataStream.readBoolean
    val fileName = StringRef.toString(dataStream.readName)
    val methodNames = for (i <- 1 to dataStream.readInt) yield StringRef.toString(dataStream.readName)
    val parent = parentStub.asInstanceOf[StubElement[PsiElement]]
    new ScTypeDefinitionStubImpl(parent, this, name, qualName, fileName, methodNames.toArray, isPO)
  }

  def indexStub(stub: ScTypeDefinitionStub, sink: IndexSink) = {
    val name = stub.getName
    if (name != null) {
      sink.occurrence(ScalaIndexKeys.SHORT_NAME_KEY, name)
    }
    val fqn = stub.qualName
    if (fqn != null) {
      sink.occurrence[PsiClass, java.lang.Integer](ScalaIndexKeys.FQN_KEY, fqn.hashCode)
    }
    if (stub.isPackageObject) {
      sink.occurrence[PsiClass, java.lang.Integer](ScalaIndexKeys.PACKAGE_OBJECT_KEY, fqn.hashCode)
    }
    val methodNames = stub.methodNames
    for (name <- methodNames) {
      sink.occurrence(ScalaIndexKeys.METHOD_NAME_TO_CLASS_KEY, name)
    }
  }
}