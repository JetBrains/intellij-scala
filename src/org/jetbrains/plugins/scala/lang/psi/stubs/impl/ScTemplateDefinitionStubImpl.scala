package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.impl.java.stubs.PsiClassStub
import com.intellij.psi.{PsiElement, PsiClass}
import com.intellij.util.io.StringRef
import com.intellij.psi.stubs.{StubElement, IStubElementType, StubBase}
import api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}
import com.intellij.psi.tree.IElementType

/**
 * @author ilyas
 */

class ScTemplateDefinitionStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                        elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
extends StubBaseWrapper[ScTemplateDefinition](parent, elemType) with ScTemplateDefinitionStub {

  var myName: String = _
  var myQualName: String = _
  var myJavaQualName: String = _
  var mySourceFileName: String = _
  var myMethodNames: Array[String] = Array[String]()
  var myJavaName: String = _
  var myAdditionalJavaNames: Array[String] = Array.empty
  private var _isScriptFileClass: Boolean = _
  private var _isPackageObject: Boolean = _
  private var _isDeprecated: Boolean = _
  private var _isImplicitObject: Boolean = _
  private var _isImplicitClass: Boolean = _
  private var local: Boolean = false

  def  this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          name: String,
          qualName: String,
          javaQualName: String,
          sourceFileName: String,
          methodNames: Array[String],
          isPackageObject: Boolean,
          isScriptFileClass: Boolean,
          isDeprecated: Boolean,
          isImplicitObject: Boolean,
          isImplicitClass: Boolean,
          javaName: String,
          additionalJavaNames: Array[String],
          isLocal: Boolean) {
    this (parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    mySourceFileName = sourceFileName
    myName = name
    myQualName = qualName
    myJavaQualName = javaQualName
    myMethodNames = methodNames
    myJavaName = javaName
    myAdditionalJavaNames = additionalJavaNames
    this._isPackageObject = isPackageObject
    _isScriptFileClass = isScriptFileClass
    _isDeprecated = isDeprecated
    _isImplicitObject = isImplicitObject
    _isImplicitClass = isImplicitClass
    local = isLocal
  }

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          name: StringRef,
          qualName: StringRef,
          javaQualName: StringRef,
          sourceFileName: StringRef,
          methodNames: Array[StringRef],
          isPackageObject: Boolean,
          isScriptFileClass: Boolean,
          isDeprecated: Boolean,
          isImplicitObject: Boolean,
          isImplicitClass: Boolean,
          javaName: StringRef,
          additionalJavaNames: Array[StringRef],
          isLocal: Boolean) {
    this (parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    mySourceFileName = StringRef.toString(sourceFileName)
    myName = StringRef.toString(name)
    myQualName = StringRef.toString(qualName)
    myJavaQualName = StringRef.toString(javaQualName)
    myMethodNames = methodNames.map(StringRef.toString(_))
    myJavaName = StringRef.toString(javaName)
    myAdditionalJavaNames = additionalJavaNames.map(StringRef.toString(_))
    this._isPackageObject = isPackageObject
    _isScriptFileClass = isScriptFileClass
    _isDeprecated = isDeprecated
    _isImplicitObject = isImplicitObject
    _isImplicitClass = isImplicitClass
    local = isLocal
  }


  def isLocal: Boolean = local

  def isPackageObject: Boolean = _isPackageObject

  def sourceFileName = mySourceFileName

  def qualName = myQualName

  def javaQualName = myJavaQualName

  def getName = myName

  def methodNames: Array[String] = myMethodNames

  def isScriptFileClass: Boolean = _isScriptFileClass

  def isDeprecated: Boolean = _isDeprecated

  def isImplicitObject: Boolean = _isImplicitObject

  def isImplicitClass: Boolean = _isImplicitClass

  //todo PsiClassStub methods
  def getLanguageLevel: LanguageLevel = LanguageLevel.JDK_1_5
  def isEnum: Boolean = false
  def isInterface: Boolean = false
  def isAnonymous: Boolean = false
  def isAnonymousInQualifiedNew: Boolean = false
  def isAnnotationType: Boolean = false
  def hasDeprecatedAnnotation: Boolean = false
  def isEnumConstantInitializer: Boolean = false
  def getBaseClassReferenceText: String = null
  def additionalJavaNames: Array[String] = myAdditionalJavaNames
  def javaName: String = myJavaName
}