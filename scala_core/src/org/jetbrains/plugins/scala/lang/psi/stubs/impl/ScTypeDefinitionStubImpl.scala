package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.impl.java.stubs.PsiClassStub
import com.intellij.psi.{PsiElement, PsiClass}
import com.intellij.util.io.StringRef
import com.intellij.psi.stubs.{StubElement, IStubElementType, StubBase}
import api.toplevel.typedef.ScTypeDefinition

/**
 * @author ilyas
 */

class ScTypeDefinitionStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                        elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
extends StubBaseWrapper[ScTypeDefinition](parent, elemType) with ScTypeDefinitionStub {

  var myName: StringRef = _
  var myQualName: StringRef = _
  var mySourceFileName: StringRef = _

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement],
          name: String,
          qualName: String,
          sourceFileName: String) {
    this (parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    mySourceFileName = StringRef.fromString(sourceFileName)
    myName = StringRef.fromString(name)
    myQualName = StringRef.fromString(qualName)
  }

  def sourceFileName = StringRef.toString(mySourceFileName)

  def qualName = StringRef.toString(myQualName)

  def getName = StringRef.toString(myName)

  //todo PsiClassStub methods
  def getLanguageLevel: LanguageLevel = LanguageLevel.JDK_1_5
  def isEnum: Boolean = false
  def isInterface: Boolean = false
  def isAnonymous: Boolean = false
  def isAnonymousInQualifiedNew: Boolean = false
  def isAnnotationType: Boolean = false
  def isDeprecated: Boolean = false
  def hasDeprecatedAnnotation: Boolean = false
  def isEnumConstantInitializer: Boolean = false
  def getBaseClassReferenceText: String = null

}