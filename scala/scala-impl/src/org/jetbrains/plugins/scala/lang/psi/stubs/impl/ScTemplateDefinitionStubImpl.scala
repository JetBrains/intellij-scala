package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
  * @author ilyas
  */
final class ScTemplateDefinitionStubImpl[TypeDef <: ScTemplateDefinition](parent: StubElement[_ <: PsiElement],
                                                                          elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                                                                          nameRef: String,
                                                                          val qualifiedName: String,
                                                                          val javaQualifiedName: String,
                                                                          val isPackageObject: Boolean,
                                                                          val isScriptFileClass: Boolean,
                                                                          val sourceFileName: String,
                                                                          val isDeprecated: Boolean,
                                                                          val isImplicitObject: Boolean,
                                                                          val isImplicitClass: Boolean,
                                                                          val javaName: String,
                                                                          val additionalJavaName: Option[String],
                                                                          val isLocal: Boolean,
                                                                          val isVisibleInJava: Boolean,
                                                                          val implicitType: Option[String])
  extends ScNamedStubBase[TypeDef](parent, elementType, nameRef)
    with ScTemplateDefinitionStub[TypeDef] {

  override def getQualifiedName: String = qualifiedName

  override def getSourceFileName: String = sourceFileName

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