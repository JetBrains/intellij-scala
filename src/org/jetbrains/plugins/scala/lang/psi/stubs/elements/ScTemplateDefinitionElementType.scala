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
import com.intellij.psi.impl.java.stubs.index.{JavaFullClassNameIndex, JavaShortClassNameIndex}
import api.expr.ScAnnotation

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
    val isSFC = psi.isScriptFileClass

    val isDepr = psi.isInstanceOf[ScTypeDefinition] && psi.getModifierList != null &&
      !psi.getModifierList.getAnnotations.forall(p => p match {
        case a: ScAnnotation => {
          val typeText = a.constructor.typeElement.getText
          typeText != "deprecated" && typeText != "scala.deprecated"
        }
        case _ => true
      })

    new ScTemplateDefinitionStubImpl[ParentPsi](parent, this, psi.getName, psi.getQualifiedName,
      fileName, signs, isPO, isSFC, isDepr)
  }

  def serialize(stub: ScTemplateDefinitionStub, dataStream: StubOutputStream) {
    dataStream.writeName(stub.getName)
    dataStream.writeName(stub.qualName)
    dataStream.writeBoolean(stub.isPackageObject)
    dataStream.writeBoolean(stub.isScriptFileClass)
    dataStream.writeName(stub.sourceFileName)
    val methodNames = stub.methodNames
    dataStream.writeInt(methodNames.length)
    for (name <- methodNames) dataStream.writeName(name)
    dataStream.writeBoolean(stub.isDeprecated)
  }

  override def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScTemplateDefinitionStub = {
    val name = dataStream.readName
    val qualName = dataStream.readName
    val isPO = dataStream.readBoolean
    val isSFC = dataStream.readBoolean
    val fileName = dataStream.readName
    val length = dataStream.readInt
    val methodNames = new Array[StringRef](length)
    for (i <- 0 until length) methodNames(i) = dataStream.readName
    val parent = parentStub.asInstanceOf[StubElement[PsiElement]]
    val isDepr = dataStream.readBoolean
    new ScTemplateDefinitionStubImpl(parent, this, name, qualName, fileName, methodNames, isPO, isSFC, isDepr)
  }

  def indexStub(stub: ScTemplateDefinitionStub, sink: IndexSink) {
    if (stub.isScriptFileClass) return
    val name = stub.getName
    if (name != null) {
      sink.occurrence(ScalaIndexKeys.SHORT_NAME_KEY, name)
      sink.occurrence(JavaShortClassNameIndex.KEY, name)
    }
    val fqn = stub.qualName
    if (fqn != null) {
      sink.occurrence[PsiClass, java.lang.Integer](ScalaIndexKeys.FQN_KEY, fqn.hashCode)
      sink.occurrence[PsiClass, java.lang.Integer](JavaFullClassNameIndex.KEY, fqn.hashCode)
      val i = fqn.lastIndexOf(".")
      if (i == -1) {
        sink.occurrence(ScalaIndexKeys.CLASS_NAME_IN_PACKAGE_KEY, "")
      } else {
        sink.occurrence(ScalaIndexKeys.CLASS_NAME_IN_PACKAGE_KEY, fqn.substring(0, i))
      }
    }
    if (stub.isPackageObject) {
      sink.occurrence[PsiClass, java.lang.Integer](ScalaIndexKeys.PACKAGE_OBJECT_KEY,
        if (name != "`package`") fqn.hashCode else {
          val index = fqn.lastIndexWhere(_ == '.')
          if (index < 0) "".hashCode
          else fqn.substring(0, index).hashCode
        })
    }
  }
}