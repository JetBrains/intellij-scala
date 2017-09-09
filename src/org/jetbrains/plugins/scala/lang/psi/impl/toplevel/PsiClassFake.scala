package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel

import _root_.java.util.{Collection, Collections, List}

import com.intellij.openapi.util.Pair
import com.intellij.psi.PsiReferenceList.Role
import com.intellij.psi._
import com.intellij.psi.meta.PsiMetaData
import org.jetbrains.plugins.scala.lang.psi.adapters.PsiClassAdapter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScDocCommentOwner


/**
 * @author ilyas
 */

trait PsiClassFake extends PsiClassAdapter with PsiReferenceList with ScDocCommentOwner {
  //todo: this methods from PsiReferenceList to avoid NPE. It's possible for asking different roles, so we can
  //todo: have problems for simple implementation of them
  def getRole: Role = Role.EXTENDS_LIST
  def getReferencedTypes: Array[PsiClassType] = PsiClassType.EMPTY_ARRAY
  def getReferenceElements: Array[PsiJavaCodeReferenceElement] = PsiJavaCodeReferenceElement.EMPTY_ARRAY

  def isInterface: Boolean = false

  def isAnnotationType: Boolean = false

  def isEnum: Boolean = false

  def getExtendsList: PsiReferenceList = this //todo: to avoid NPE from Java

  def getImplementsList: PsiReferenceList = this //todo: to avoid NPE from Java

  def getExtendsListTypes: Array[PsiClassType] = PsiClassType.EMPTY_ARRAY

  def getImplementsListTypes: Array[PsiClassType] = PsiClassType.EMPTY_ARRAY

  def getSuperClass: PsiClass = null

  def getInterfaces: Array[PsiClass] = PsiClass.EMPTY_ARRAY

  def getSupers: Array[PsiClass] = PsiClass.EMPTY_ARRAY

  def getSuperTypes: Array[PsiClassType] = PsiClassType.EMPTY_ARRAY

  def psiFields: Array[PsiField] = PsiField.EMPTY_ARRAY // todo

  def getConstructors: Array[PsiMethod] = PsiMethod.EMPTY_ARRAY // todo

  def psiInnerClasses: Array[PsiClass] = PsiClass.EMPTY_ARRAY // todo

  def getInitializers: Array[PsiClassInitializer] = PsiClassInitializer.EMPTY_ARRAY

  def getAllFields: Array[PsiField] = getFields

  def getAllMethods: Array[PsiMethod] = getMethods

  def getAllInnerClasses: Array[PsiClass] = getInnerClasses

  def findFieldByName(name: String, checkBases: Boolean): PsiField = null

  def findMethodBySignature(patternMethod: PsiMethod, checkBases: Boolean): PsiMethod = null

  def findMethodsBySignature(patternMethod: PsiMethod, checkBases: Boolean): Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  def findMethodsAndTheirSubstitutorsByName(name: String, checkBases: Boolean): List[Pair[PsiMethod, PsiSubstitutor]] = Collections.emptyList[Pair[PsiMethod, PsiSubstitutor]]

  def findMethodsAndTheirSubstitutors: List[Pair[PsiMethod, PsiSubstitutor]] = Collections.emptyList[Pair[PsiMethod, PsiSubstitutor]]

  def getAllMethodsAndTheirSubstitutors: List[Pair[PsiMethod, PsiSubstitutor]] = Collections.emptyList[Pair[PsiMethod, PsiSubstitutor]]

  def findInnerClassByName(name: String, checkBases: Boolean): PsiClass = null

  def getLBrace: PsiJavaToken = null

  def getRBrace: PsiJavaToken = null

  def getScope: PsiElement = null

  def isInheritor(baseClass: PsiClass, checkDeep: Boolean): Boolean = false

  def isInheritorDeep(baseClass: PsiClass, classToPass: PsiClass): Boolean = false

  def getVisibleSignatures: Collection[HierarchicalMethodSignature] = Collections.emptyList[HierarchicalMethodSignature]

  def getModifierList: PsiModifierList = ScalaPsiUtil.getEmptyModifierList(getManager)

  def hasModifierProperty(name: String): Boolean = name.equals(PsiModifier.PUBLIC)

  def getMetaData: PsiMetaData = null

  def isMetaEnough: Boolean = false

  def hasTypeParameters: Boolean = false

  def getTypeParameterList: PsiTypeParameterList = null

  def psiTypeParameters: Array[PsiTypeParameter] = PsiTypeParameter.EMPTY_ARRAY

  def findMethodsByName(name: String, checkBases: Boolean): Array[PsiMethod] = Array[PsiMethod]()

  def psiMethods: Array[PsiMethod] = Array[PsiMethod]()

  def getQualifiedName: String = null

  def getContainingClass: PsiClass = null
}