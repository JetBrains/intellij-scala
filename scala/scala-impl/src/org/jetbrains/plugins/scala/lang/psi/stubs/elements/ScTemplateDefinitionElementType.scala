package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.{ASTNode, Language}
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScNewTemplateDefinitionImpl
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.{ScClassImpl, ScObjectImpl, ScTraitImpl}
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTemplateDefinitionStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.{ImplicitConversionIndex, ImplicitInstanceIndex}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
  * @author ilyas, alefas
  */
abstract class ScTemplateDefinitionElementType[TypeDef <: ScTemplateDefinition](debugName: String,
                                                                                language: Language = ScalaLanguage.INSTANCE)
  extends ScStubElementType[ScTemplateDefinitionStub[TypeDef], TypeDef](debugName, language) {

  override def serialize(stub: ScTemplateDefinitionStub[TypeDef], dataStream: StubOutputStream): Unit = {
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
    dataStream.writeOptionName(stub.implicitType)
  }

  override def deserialize(dataStream: StubInputStream,
                           parentStub: StubElement[_ <: PsiElement]) = new ScTemplateDefinitionStubImpl(
    parentStub,
    this,
    nameRef = dataStream.readNameString,
    qualifiedName = dataStream.readNameString,
    javaQualifiedName = dataStream.readNameString,
    isPackageObject = dataStream.readBoolean,
    isScriptFileClass = dataStream.readBoolean,
    sourceFileName = dataStream.readNameString,
    isDeprecated = dataStream.readBoolean,
    isImplicitObject = dataStream.readBoolean,
    isImplicitClass = dataStream.readBoolean,
    javaName = dataStream.readNameString,
    additionalJavaName = dataStream.readOptionName,
    isLocal = dataStream.readBoolean,
    isVisibleInJava = dataStream.readBoolean,
    implicitType = dataStream.readOptionName
  )

  override def createStubImpl(definition: TypeDef,
                              parent: StubElement[_ <: PsiElement]): ScTemplateDefinitionStub[TypeDef] = {
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
      case a: ScAnnotation => a.constructorInvocation.typeElement.getText
    }.exists {
      case "deprecated" | "scala.deprecated" => true
      case _ => false
    }

    val maybeAdditionalJavaName = maybeTypeDefinition
      .flatMap(_.additionalClassJavaName)

    val isLocal = definition.containingClass == null &&
      PsiTreeUtil.getParentOfType(definition, classOf[ScTemplateDefinition]) != null

    val isVisibleInJava = definition.parents.forall {
      case o: ScObject => !o.isPackageObject
      case _ => true
    }

    val implicitType = definition match {
      case obj: ScObject if obj.getModifierList.isImplicit =>
        ScImplicitInstanceStub.mainSuperClassName(obj)
      case _ => None
    }

    new ScTemplateDefinitionStubImpl(
      parent,
      this,
      nameRef = definition.name,
      qualifiedName = definition.qualifiedName,
      javaQualifiedName = definition.getQualifiedName,
      isPackageObject = maybeTypeDefinition.exists(_.isPackageObject),
      isScriptFileClass = definition.isScriptFileClass,
      sourceFileName = fileName,
      isDeprecated = isDeprecated,
      isImplicitObject = definition.isInstanceOf[ScObject] && definition.hasModifierProperty("implicit"),
      isImplicitClass = definition.isInstanceOf[ScClass] && definition.hasModifierProperty("implicit"),
      javaName = definition.getName,
      additionalJavaName = maybeAdditionalJavaName,
      isLocal = isLocal,
      isVisibleInJava = isVisibleInJava,
      implicitType = implicitType
    )
  }

  override def indexStub(stub: ScTemplateDefinitionStub[TypeDef], sink: IndexSink): Unit = {
    import JavaStubIndexKeys._
    import index.ScalaIndexKeys._

    if (stub.isScriptFileClass) return
    val name = ScalaNamesUtil.cleanFqn(stub.getName)
    if (name != null) {
      sink.occurrence(SHORT_NAME_KEY, name)
    }
    val javaName = ScalaNamesUtil.cleanFqn(stub.javaName)
    if (javaName != null && stub.isVisibleInJava) sink.occurrence(CLASS_SHORT_NAMES, javaName)
    else sink.occurrence(NOT_VISIBLE_IN_JAVA_SHORT_NAME_KEY, name)
    sink.occurrence(ALL_CLASS_NAMES, javaName)
    val additionalNames = stub.additionalJavaName
    for (name <- additionalNames) {
      sink.occurrence(ALL_CLASS_NAMES, name)
    }
    val javaFqn = ScalaNamesUtil.cleanFqn(stub.javaQualifiedName)
    if (javaFqn != null && !stub.isLocal && stub.isVisibleInJava) {
      sink.occurrence[PsiClass, java.lang.Integer](CLASS_FQN, javaFqn.hashCode)
      val i = javaFqn.lastIndexOf(".")
      val pack =
        if (i == -1) ""
        else javaFqn.substring(0, i)
      sink.occurrence(JAVA_CLASS_NAME_IN_PACKAGE_KEY, pack)
    }

    val fqn = ScalaNamesUtil.cleanFqn(stub.getQualifiedName)
    if (fqn != null && !stub.isLocal) {
      sink.occurrence[PsiClass, java.lang.Integer](FQN_KEY, fqn.hashCode)
      val i = fqn.lastIndexOf(".")
      val pack =
        if (i == -1) ""
        else fqn.substring(0, i)
      sink.occurrence(CLASS_NAME_IN_PACKAGE_KEY, pack)
      if (stub.isImplicitObject) sink.occurrence(IMPLICIT_OBJECT_KEY, pack)
      if (stub.isImplicitClass) ImplicitConversionIndex.occurrence(sink)
      ImplicitInstanceIndex.occurrence(sink, stub.implicitType)
    }
    if (stub.isPackageObject) {
      val packageName = fqn.stripSuffix(".package")
      val shortName = {
        val index = packageName.lastIndexOf('.')
        if (index < 0) packageName else packageName.substring(index + 1, packageName.length)
      }
      sink.occurrence[PsiClass, java.lang.Integer](PACKAGE_OBJECT_KEY, packageName.hashCode)
      sink.occurrence[PsiClass, String](PACKAGE_OBJECT_SHORT_NAME_KEY, shortName)
    }
  }
}

object ClassDefinition extends ScTemplateDefinitionElementType[ScClass]("class definition") {

  override def createElement(node: ASTNode) = new ScClassImpl(null, null, node)

  override def createPsi(stub: ScTemplateDefinitionStub[ScClass]) = new ScClassImpl(stub, this, null)
}

object TraitDefinition extends ScTemplateDefinitionElementType[ScTrait]("trait definition") {

  override def createElement(node: ASTNode) = new ScTraitImpl(null, null, node)

  override def createPsi(stub: ScTemplateDefinitionStub[ScTrait]) = new ScTraitImpl(stub, this, null)
}

object ObjectDefinition extends ScTemplateDefinitionElementType[ScObject]("object definition") {

  override def createElement(node: ASTNode) = new ScObjectImpl(null, null, node)

  override def createPsi(stub: ScTemplateDefinitionStub[ScObject]) = new ScObjectImpl(stub, this, null)
}

object NewTemplateDefinition extends ScTemplateDefinitionElementType[ScNewTemplateDefinition]("new template definition") {

  override def createElement(node: ASTNode) = new ScNewTemplateDefinitionImpl(null, null, node)

  override def createPsi(stub: ScTemplateDefinitionStub[ScNewTemplateDefinition]) = new ScNewTemplateDefinitionImpl(stub, this, null)
}