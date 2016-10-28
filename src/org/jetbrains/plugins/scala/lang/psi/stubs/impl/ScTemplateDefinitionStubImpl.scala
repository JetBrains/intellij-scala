package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.StringRefArrayExt

/**
  * @author ilyas
  */
class ScTemplateDefinitionStubImpl(parent: StubElement[_ <: PsiElement],
                                   elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                                   nameRef: StringRef,
                                   private val qualifiedNameRef: StringRef,
                                   private val javaQualifiedNameRef: StringRef,
                                   val isPackageObject: Boolean,
                                   val isScriptFileClass: Boolean,
                                   private val sourceFileNameRef: StringRef,
                                   val isDeprecated: Boolean,
                                   val isImplicitObject: Boolean,
                                   val isImplicitClass: Boolean,
                                   private val javaNameRef: StringRef,
                                   private val additionalJavaNamesRefs: Array[StringRef],
                                   val isLocal: Boolean,
                                   val isVisibleInJava: Boolean)
  extends ScNamedStubBase[ScTemplateDefinition](parent, elementType, nameRef) with ScTemplateDefinitionStub {

  override def getQualifiedName: String = StringRef.toString(qualifiedNameRef)

  override def javaQualifiedName: String = StringRef.toString(javaQualifiedNameRef)

  override def getSourceFileName: String = StringRef.toString(sourceFileNameRef)

  override def javaName: String = StringRef.toString(javaNameRef)

  override def additionalJavaNames: Array[String] = additionalJavaNamesRefs.asStrings

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