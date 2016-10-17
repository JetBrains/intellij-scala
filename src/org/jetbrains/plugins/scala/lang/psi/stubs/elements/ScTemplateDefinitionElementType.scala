package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement}
import com.intellij.util.io.StringRef.fromString
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
    dataStream.writeNames(stub.additionalJavaNames)
    dataStream.writeBoolean(stub.isLocal)
    dataStream.writeBoolean(stub.isVisibleInJava)
  }


  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScTemplateDefinitionStub =
    new ScTemplateDefinitionStubImpl(parentStub, this,
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
      additionalJavaNamesRefs = dataStream.readNames,
      isLocal = dataStream.readBoolean,
      isVisibleInJava = dataStream.readBoolean)

  override def createStub(definition: ScTemplateDefinition, parent: StubElement[_ <: PsiElement]): ScTemplateDefinitionStub = {
    val fileName = definition.containingVirtualFile.map {
      _.getName
    }.orNull

    val isPackageObject = definition match {
      case td: ScTypeDefinition => td.isPackageObject
      case _ => false
    }
    val isDeprecated = definition.isInstanceOf[ScTypeDefinition] && definition.getModifierList != null &&
      !definition.getModifierList.getAnnotations.forall {
        case a: ScAnnotation =>
          val typeText = a.constructor.typeElement.getText
          typeText != "deprecated" && typeText != "scala.deprecated"
        case _ => true
      }

    def isOkForJava(elem: ScalaPsiElement): Boolean = {
      var res = true
      var element = elem.getParent
      while (element != null && res) {
        element match {
          case o: ScObject if o.isPackageObject => res = false
          case _ =>
        }
        element = element.getParent
      }
      res
    }

    val isLocal = definition.containingClass == null &&
      PsiTreeUtil.getParentOfType(definition, classOf[ScTemplateDefinition]) != null

    new ScTemplateDefinitionStubImpl(parent, this,
      nameRef = fromString(definition.name),
      qualifiedNameRef = fromString(definition.qualifiedName),
      javaQualifiedNameRef = fromString(definition.getQualifiedName),
      isPackageObject = isPackageObject,
      isScriptFileClass = definition.isScriptFileClass,
      sourceFileNameRef = fromString(fileName),
      isDeprecated = isDeprecated,
      isImplicitObject = definition.isInstanceOf[ScObject] && definition.hasModifierProperty("implicit"),
      isImplicitClass = definition.isInstanceOf[ScClass] && definition.hasModifierProperty("implicit"),
      javaNameRef = fromString(definition.getName),
      additionalJavaNamesRefs = definition.additionalJavaNames.asReferences,
      isLocal = isLocal,
      isVisibleInJava = isOkForJava(definition))
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
    val additionalNames = stub.additionalJavaNames
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
      if (stub.isImplicitObject) {
        sink.occurrence(ScalaIndexKeys.IMPLICIT_OBJECT_KEY, pack)
      }
      if (stub.isImplicitClass) {
        this.indexImplicit(sink)
      }
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