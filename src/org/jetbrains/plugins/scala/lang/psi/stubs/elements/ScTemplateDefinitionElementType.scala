package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.psi.{PsiClass, PsiElement}
import com.intellij.util.io.StringRef
import impl.ScTemplateDefinitionStubImpl
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import index.ScalaIndexKeys
import api.toplevel.typedef.{ScClass, ScTemplateDefinition, ScObject, ScTypeDefinition}
import api.expr.ScAnnotation
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys
import extensions.toPsiMemberExt
import com.intellij.psi.util.PsiTreeUtil

/**
 * @author ilyas, alefas
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

    val isImplicitObject = psi.isInstanceOf[ScObject] && psi.hasModifierProperty("implicit")
    val isImplicitClass = psi.isInstanceOf[ScClass] && psi.hasModifierProperty("implicit")

    val javaName = psi.javaName
    val additionalJavaNames = psi.additionalJavaNames

    new ScTemplateDefinitionStubImpl[ParentPsi](parent, this, psi.name, psi.qualifiedName, psi.getQualifiedName,
      fileName, signs, isPO, isSFC, isDepr, isImplicitObject, isImplicitClass, javaName, additionalJavaNames,
      psi.containingClass == null && PsiTreeUtil.getParentOfType(psi, classOf[ScTemplateDefinition]) != null)
  }

  def serialize(stub: ScTemplateDefinitionStub, dataStream: StubOutputStream) {
    dataStream.writeName(stub.getName)
    dataStream.writeName(stub.qualName)
    dataStream.writeName(stub.javaQualName)
    dataStream.writeBoolean(stub.isPackageObject)
    dataStream.writeBoolean(stub.isScriptFileClass)
    dataStream.writeName(stub.sourceFileName)
    val methodNames = stub.methodNames
    dataStream.writeInt(methodNames.length)
    for (name <- methodNames) dataStream.writeName(name)
    dataStream.writeBoolean(stub.isDeprecated)
    dataStream.writeBoolean(stub.isImplicitObject)
    dataStream.writeBoolean(stub.isImplicitClass)
    dataStream.writeName(stub.javaName)
    val additionalNames = stub.additionalJavaNames
    dataStream.writeInt(additionalNames.length)
    for (name <- additionalNames) dataStream.writeName(name)
    dataStream.writeBoolean(stub.isLocal)
  }

  override def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScTemplateDefinitionStub = {
    val name = dataStream.readName
    val qualName = dataStream.readName
    val javaQualName = dataStream.readName()
    val isPO = dataStream.readBoolean
    val isSFC = dataStream.readBoolean
    val fileName = dataStream.readName
    val length = dataStream.readInt
    val methodNames = new Array[StringRef](length)
    for (i <- 0 until length) methodNames(i) = dataStream.readName
    val parent = parentStub.asInstanceOf[StubElement[PsiElement]]
    val isDepr = dataStream.readBoolean
    val isImplcitObject = dataStream.readBoolean
    val isImplcitClass = dataStream.readBoolean
    val javaName = dataStream.readName()
    val lengthA = dataStream.readInt()
    val additionalNames = new Array[StringRef](lengthA)
    for (i <- 0 until lengthA) additionalNames(i) = dataStream.readName()
    val isLocal = dataStream.readBoolean()
    new ScTemplateDefinitionStubImpl(parent, this, name, qualName, javaQualName, fileName, methodNames, isPO, isSFC, isDepr,
      isImplcitObject, isImplcitClass, javaName, additionalNames, isLocal)
  }

  def indexStub(stub: ScTemplateDefinitionStub, sink: IndexSink) {
    if (stub.isScriptFileClass) return
    val name = stub.getName
    if (name != null) {
      sink.occurrence(ScalaIndexKeys.SHORT_NAME_KEY, name)
    }
    val javaName = stub.javaName
    if (javaName != null) sink.occurrence(JavaStubIndexKeys.CLASS_SHORT_NAMES, javaName)
    sink.occurrence(ScalaIndexKeys.ALL_CLASS_NAMES, javaName)
    val additionalNames = stub.additionalJavaNames
    for (name <- additionalNames) {
      sink.occurrence(ScalaIndexKeys.ALL_CLASS_NAMES, name)
    }
    val javaFqn = stub.javaQualName
    if (javaFqn != null && !stub.isLocal) {
      sink.occurrence[PsiClass, java.lang.Integer](JavaStubIndexKeys.CLASS_FQN, javaFqn.hashCode)
      val i = javaFqn.lastIndexOf(".")
      val pack =
        if (i == -1) ""
        else javaFqn.substring(0, i)
      sink.occurrence(ScalaIndexKeys.JAVA_CLASS_NAME_IN_PACKAGE_KEY, pack)
    }
    val fqn = stub.qualName
    if (fqn != null && !stub.isLocal) {
      sink.occurrence[PsiClass, java.lang.Integer](ScalaIndexKeys.FQN_KEY, fqn.hashCode)
      val i = fqn.lastIndexOf(".")
      val pack =
        if (i == -1) ""
        else fqn.substring(0, i)
      sink.occurrence(ScalaIndexKeys.CLASS_NAME_IN_PACKAGE_KEY, pack)
      if (stub.isImplicitObject) {
        sink.occurrence(ScalaIndexKeys.IMPLICIT_OBJECT_KEY, pack)
      }
      if (stub.isImplicitClass) {
        sink.occurrence(ScalaIndexKeys.IMPLICITS_KEY, "implicit")
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