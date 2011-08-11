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

/**
 * @author ilyas
 */

class ScTemplateDefinitionStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                        elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
extends StubBaseWrapper[ScTemplateDefinition](parent, elemType) with ScTemplateDefinitionStub {

  var myName: String = _
  var myQualName: String = _
  var mySourceFileName: String = _
  var myMethodNames: Array[String] = Array[String]()
  private var _isScriptFileClass: Boolean = _
  private var _isPackageObject: Boolean = _
  private var _isDeprecated: Boolean = _
  private var _isImplicitObject: Boolean = _

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          name: String,
          qualName: String,
          sourceFileName: String,
          methodNames: Array[String],
          isPackageObject: Boolean,
          isScriptFileClass: Boolean,
          isDeprecated: Boolean,
          isImplicitObject: Boolean) {
    this (parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    mySourceFileName = sourceFileName
    myName = name
    myQualName = qualName
    myMethodNames = methodNames
    this._isPackageObject = isPackageObject
    _isScriptFileClass = isScriptFileClass
    _isDeprecated = isDeprecated
    _isImplicitObject = isImplicitObject
  }

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          name: StringRef,
          qualName: StringRef,
          sourceFileName: StringRef,
          methodNames: Array[StringRef],
          isPackageObject: Boolean,
          isScriptFileClass: Boolean,
          isDeprecated: Boolean,
          isImplicitObject: Boolean) {
    this (parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    mySourceFileName = StringRef.toString(sourceFileName)
    myName = StringRef.toString(name)
    myQualName = StringRef.toString(qualName)
    myMethodNames = methodNames.map(StringRef.toString(_))
    this._isPackageObject = isPackageObject
    _isScriptFileClass = isScriptFileClass
    _isDeprecated = isDeprecated
    _isImplicitObject = isImplicitObject
  }


  def isPackageObject: Boolean = _isPackageObject

  def sourceFileName = mySourceFileName

  def qualName = myQualName

  def getName = myName

  def methodNames: Array[String] = myMethodNames

  def isScriptFileClass: Boolean = _isScriptFileClass

  def isDeprecated: Boolean = _isDeprecated

  def isImplicitObject: Boolean = _isImplicitObject

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
}