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
import com.intellij.util.ArrayUtil.EMPTY_STRING_ARRAY
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScNewTemplateDefinitionImpl
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.{ScClassImpl, ScObjectImpl, ScTraitImpl}
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTemplateDefinitionStubImpl
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
    dataStream.writeName(stub.getSourceFileName)
    dataStream.writeName(stub.javaName)
    dataStream.writeName(stub.javaQualifiedName)
    dataStream.writeOptionName(stub.additionalJavaName)
    dataStream.writeBoolean(stub.isPackageObject)
    dataStream.writeBoolean(stub.isScriptFileClass)
    dataStream.writeBoolean(stub.isDeprecated)
    dataStream.writeBoolean(stub.isLocal)
    dataStream.writeBoolean(stub.isVisibleInJava)
    dataStream.writeBoolean(stub.isImplicitObject)
    dataStream.writeBoolean(stub.isImplicitConversion)
    dataStream.writeNames(stub.implicitClassNames)
  }

  override def deserialize(dataStream: StubInputStream,
                           parentStub: StubElement[_ <: PsiElement]) = new ScTemplateDefinitionStubImpl(
    parentStub,
    this,
    nameRef = dataStream.readNameString,
    getQualifiedName = dataStream.readNameString,
    getSourceFileName = dataStream.readNameString,
    javaName = dataStream.readNameString,
    javaQualifiedName = dataStream.readNameString,
    additionalJavaName = dataStream.readOptionName,
    isPackageObject = dataStream.readBoolean,
    isScriptFileClass = dataStream.readBoolean,
    isDeprecated = dataStream.readBoolean,
    isLocal = dataStream.readBoolean,
    isVisibleInJava = dataStream.readBoolean,
    isImplicitObject = dataStream.readBoolean,
    isImplicitConversion = dataStream.readBoolean,
    implicitClassNames = dataStream.readNames
  )

  override def createStubImpl(definition: TypeDef,
                              parent: StubElement[_ <: PsiElement]): ScTemplateDefinitionStub[TypeDef] = {
    val fileName = definition.containingVirtualFile.map(_.getName).orNull

    val (isDeprecated, additionalJavaName, isPackageObject) = definition match {
      case typeDefinition: ScTypeDefinition =>
        val annotations = definition.getModifierList match {
          case null => Array.empty
          case list => list.getAnnotations
        }

        val isScalaDeprecated = annotations.exists {
          case annotation: ScAnnotation =>
            val text = annotation.constructorInvocation.typeElement.getText
            text == "deprecated" || text == "scala.deprecated"
          case _ => false
        }

        (
          isScalaDeprecated,
          typeDefinition.additionalClassJavaName,
          typeDefinition.isPackageObject
        )
      case _ =>
        (false, None, false)
    }

    val isLocal = definition.containingClass == null &&
      PsiTreeUtil.getParentOfType(definition, classOf[ScTemplateDefinition]) != null

    val isVisibleInJava = definition.parents.forall {
      case o: ScObject => !o.isPackageObject
      case _ => true
    }

    val isImplicit = definition.hasModifierPropertyScala("implicit")
    val qualifiedName = definition.qualifiedName

    val (isImplicitObject, isImplicitConversion, implicitClassNames) = definition match {
      case obj: ScObject if isImplicit => (true, false, ScImplicitInstanceStub.superClassNames(obj))
      case _: ScClass if isImplicit => (false, true, Array(qualifiedName))
      case _ => (false, false, EMPTY_STRING_ARRAY)
    }

    new ScTemplateDefinitionStubImpl(
      parent,
      this,
      nameRef = definition.name,
      getQualifiedName = definition.qualifiedName,
      getSourceFileName = fileName,
      javaName = definition.getName,
      javaQualifiedName = definition.getQualifiedName,
      additionalJavaName = additionalJavaName,
      isPackageObject = isPackageObject,
      isScriptFileClass = definition.isScriptFileClass,
      isDeprecated = isDeprecated,
      isLocal = isLocal,
      isVisibleInJava = isVisibleInJava,
      isImplicitObject = isImplicitObject,
      isImplicitConversion = isImplicitConversion,
      implicitClassNames = implicitClassNames
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

      stub.indexImplicits(sink)
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