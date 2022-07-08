package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

final class ScTemplateDefinitionStubImpl[TypeDef <: ScTemplateDefinition](
  parent:                                        StubElement[_ <: PsiElement],
  elementType:                                   IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
  nameRef:                                       String,
  override val getQualifiedName:                 String,
  override val getSourceFileName:                String,
  override val javaName:                         String,
  override val javaQualifiedName:                String,
  override val additionalJavaName:               Option[String],
  override val isPackageObject:                  Boolean,
  override val isScriptFileClass:                Boolean,
  override val isDeprecated:                     Boolean,
  override val isLocal:                          Boolean,
  override val isVisibleInJava:                  Boolean,
  override val isImplicitObject:                 Boolean,
  override val implicitConversionParameterClass: Option[String],
  override val implicitClassNames:               Array[String],
  override val isTopLevel:                       Boolean,
  override val topLevelQualifier:                Option[String],
  override val isGiven:                          Boolean,
  override val givenClassNames:                  Array[String],
) extends ScNamedStubBase[TypeDef](parent, elementType, nameRef)
    with ScTemplateDefinitionStub[TypeDef] {

  //todo PsiClassStub methods
  override def getLanguageLevel: LanguageLevel = LanguageLevel.JDK_1_5

  override def isEnum: Boolean = false

  override def isInterface: Boolean = false

  override def isAnonymous: Boolean = false

  override def isAnonymousInQualifiedNew: Boolean = false

  override def isAnnotationType: Boolean = false

  override def hasDeprecatedAnnotation: Boolean = false

  override def isEnumConstantInitializer: Boolean = false

  override def getBaseClassReferenceText: String = null
}
