package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.psi.{PsiClass, PsiElement}
import com.intellij.util.io.StringRef
import impl.ScTemplateDefinitionStubImpl
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import com.intellij.lang.ASTNode
import index.{ScFullClassNameIndex, ScalaIndexKeys, ScShortClassNameIndex}
import api.toplevel.typedef.{ScTemplateDefinition, ScObject, ScTypeDefinition}

/**
 * @author ilyas
 */

abstract class ScTemplateDefinitionElementType[TypeDef <: ScTemplateDefinition](debugName: String)
extends ScStubElementType[ScTemplateDefinitionStub, ScTemplateDefinition](debugName) {

  override def createStubImpl[ParentPsi <: PsiElement](psi: ScTemplateDefinition, parent: StubElement[ParentPsi]): ScTemplateDefinitionStub = {
    val file = psi.getContainingFile
    val fileName = if (file != null && file.getVirtualFile != null) file.getVirtualFile.getName else null
    val signs = psi.functions.map(_.name).toArray
    val isPO = psi match {
      case td: ScTypeDefinition => td.isPackageObject
      case _ => false
    }
    new ScTemplateDefinitionStubImpl[ParentPsi](parent, this, psi.getName, psi.getQualifiedName, fileName, signs, isPO)
  }

  def serialize(stub: ScTemplateDefinitionStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeName(stub.qualName)
    dataStream.writeBoolean(stub.isPackageObject)
    dataStream.writeName(stub.sourceFileName)
    val methodNames = stub.methodNames
    dataStream.writeInt(methodNames.length)
    for (name <- methodNames) dataStream.writeName(name)
  }

  override def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScTemplateDefinitionStub = {
    val name = StringRef.toString(dataStream.readName)
    val qualName = StringRef.toString(dataStream.readName)
    val isPO = dataStream.readBoolean
    val fileName = StringRef.toString(dataStream.readName)
    val methodNames = for (i <- 1 to dataStream.readInt) yield StringRef.toString(dataStream.readName)
    val parent = parentStub.asInstanceOf[StubElement[PsiElement]]
    new ScTemplateDefinitionStubImpl(parent, this, name, qualName, fileName, methodNames.toArray, isPO)
  }

  def indexStub(stub: ScTemplateDefinitionStub, sink: IndexSink) = {
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