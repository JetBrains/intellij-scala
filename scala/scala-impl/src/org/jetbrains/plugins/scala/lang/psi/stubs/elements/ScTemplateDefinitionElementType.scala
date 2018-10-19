package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTemplateDefinitionStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
  * @author ilyas, alefas
  */
abstract class ScTemplateDefinitionElementType[TypeDef <: ScTemplateDefinition](debugName: String)
  extends ScStubElementType[ScTemplateDefinitionStub, ScTemplateDefinition](debugName) {

  override def serialize(stub: ScTemplateDefinitionStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeName(stub.getQualifiedName)
    dataStream.writeName(stub.javaQualifiedName)
    dataStream.writeBoolean(stub.isPackageObject)
    dataStream.writeBoolean(stub.isScriptFileClass)
    dataStream.writeName(stub.getSourceFileName)
    dataStream.writeBoolean(stub.isDeprecated)
    dataStream.writeBoolean(stub.isImplicitObject)
    dataStream.writeBoolean(stub.isImplicitClass)
    dataStream.writeName(stub.javaName)
    dataStream.writeOptionName(stub.additionalJavaName)
    dataStream.writeBoolean(stub.isLocal)
    dataStream.writeBoolean(stub.isVisibleInJava)
  }

  override def deserialize(dataStream: StubInputStream,
                           parentStub: StubElement[_ <: PsiElement]) = new ScTemplateDefinitionStubImpl(
    parentStub,
    this,
    nameRef = dataStream.readName,
    qualifiedNameRef = dataStream.readName,
    javaQualifiedNameRef = dataStream.readName,
    isPackageObject = dataStream.readBoolean,
    isScriptFileClass = dataStream.readBoolean,
    sourceFileNameRef = dataStream.readName,
    isDeprecated = dataStream.readBoolean,
    isImplicitObject = dataStream.readBoolean,
    isImplicitClass = dataStream.readBoolean,
    javaNameRef = dataStream.readName,
    additionalJavaNameRef = dataStream.readOptionName,
    isLocal = dataStream.readBoolean,
    isVisibleInJava = dataStream.readBoolean
  )

  override def createStubImpl(definition: ScTemplateDefinition,
                              parent: StubElement[_ <: PsiElement]): ScTemplateDefinitionStub = {
    val fileName = definition.containingVirtualFile
      .map(_.getName).orNull

    val maybeTypeDefinition = definition match {
      case typeDefinition: ScTypeDefinition => Some(typeDefinition)
      case _ => None
    }

    val isDeprecated = maybeTypeDefinition.toSeq
      .flatMap { definition =>
        Option(definition.getModifierList)
      }.flatMap {
      _.getAnnotations
    }.collect {
      case a: ScAnnotation => a.constructor.typeElement.getText
    }.exists {
      case "deprecated" | "scala.deprecated" => true
      case _ => false
    }

    val maybeAdditionalJavaName = maybeTypeDefinition
      .flatMap(_.additionalJavaClass)
      .map(_.getName)

    val isLocal = definition.containingClass == null &&
      PsiTreeUtil.getParentOfType(definition, classOf[ScTemplateDefinition]) != null

    val isVisibleInJava = definition.parents.forall {
      case o: ScObject => !o.isPackageObject
      case _ => true
    }

    import StringRef.fromString
    new ScTemplateDefinitionStubImpl(
      parent,
      this,
      nameRef = fromString(definition.name),
      qualifiedNameRef = fromString(definition.qualifiedName),
      javaQualifiedNameRef = fromString(definition.getQualifiedName),
      isPackageObject = maybeTypeDefinition.exists(_.isPackageObject),
      isScriptFileClass = definition.isScriptFileClass,
      sourceFileNameRef = fromString(fileName),
      isDeprecated = isDeprecated,
      isImplicitObject = definition.isInstanceOf[ScObject] && definition.hasModifierProperty("implicit"),
      isImplicitClass = definition.isInstanceOf[ScClass] && definition.hasModifierProperty("implicit"),
      javaNameRef = fromString(definition.getName),
      additionalJavaNameRef = maybeAdditionalJavaName.asReference,
      isLocal = isLocal,
      isVisibleInJava = isVisibleInJava
    )
  }

  override def indexStub(stub: ScTemplateDefinitionStub, sink: IndexSink): Unit = {
    if (stub.isScriptFileClass) return
    val name = ScalaNamesUtil.cleanFqn(stub.getName)
    if (name != null) {
      sink.occurrence(ScalaIndexKeys.SHORT_NAME_KEY, name)
    }
    val javaName = ScalaNamesUtil.cleanFqn(stub.javaName)
    if (javaName != null && stub.isVisibleInJava) sink.occurrence(JavaStubIndexKeys.CLASS_SHORT_NAMES, javaName)
    else sink.occurrence(ScalaIndexKeys.NOT_VISIBLE_IN_JAVA_SHORT_NAME_KEY, name)
    sink.occurrence(ScalaIndexKeys.ALL_CLASS_NAMES, javaName)
    val additionalNames = stub.additionalJavaName
    for (name <- additionalNames) {
      sink.occurrence(ScalaIndexKeys.ALL_CLASS_NAMES, name)
    }
    val javaFqn = ScalaNamesUtil.cleanFqn(stub.javaQualifiedName)
    if (javaFqn != null && !stub.isLocal && stub.isVisibleInJava) {
      sink.occurrence[PsiClass, java.lang.Integer](JavaStubIndexKeys.CLASS_FQN, javaFqn.hashCode)
      val i = javaFqn.lastIndexOf(".")
      val pack =
        if (i == -1) ""
        else javaFqn.substring(0, i)
      sink.occurrence(ScalaIndexKeys.JAVA_CLASS_NAME_IN_PACKAGE_KEY, pack)
    }

    val fqn = ScalaNamesUtil.cleanFqn(stub.getQualifiedName)
    if (fqn != null && !stub.isLocal) {
      sink.occurrence[PsiClass, java.lang.Integer](ScalaIndexKeys.FQN_KEY, fqn.hashCode)
      val i = fqn.lastIndexOf(".")
      val pack =
        if (i == -1) ""
        else fqn.substring(0, i)
      sink.occurrence(ScalaIndexKeys.CLASS_NAME_IN_PACKAGE_KEY, pack)
      if (stub.isImplicitObject) sink.occurrence(ScalaIndexKeys.IMPLICIT_OBJECT_KEY, pack)
      if (stub.isImplicitClass) sink.implicitOccurence()
    }
    if (stub.isPackageObject) {
      val packageName = fqn.stripSuffix(".package")
      val shortName = {
        val index = packageName.lastIndexOf('.')
        if (index < 0) packageName else packageName.substring(index + 1, packageName.length)
      }
      sink.occurrence[PsiClass, java.lang.Integer](ScalaIndexKeys.PACKAGE_OBJECT_KEY, packageName.hashCode)
      sink.occurrence[PsiClass, String](ScalaIndexKeys.PACKAGE_OBJECT_SHORT_NAME_KEY, shortName)
    }
  }
}