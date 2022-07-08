package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel

import com.intellij.openapi.util.Pair
import com.intellij.psi.PsiReferenceList.Role
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.adapters.PsiClassAdapter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScDocCommentOwner

import _root_.java.util.{Collection, Collections, List}

trait PsiClassFake extends PsiClassAdapter with PsiReferenceList with ScDocCommentOwner {
  //todo: this methods from PsiReferenceList to avoid NPE. It's possible for asking different roles, so we can
  //todo: have problems for simple implementation of them
  override def getRole: Role = Role.EXTENDS_LIST
  override def getReferencedTypes: Array[PsiClassType] = PsiClassType.EMPTY_ARRAY
  override def getReferenceElements: Array[PsiJavaCodeReferenceElement] = PsiJavaCodeReferenceElement.EMPTY_ARRAY

  override def isInterface: Boolean = false

  override def isAnnotationType: Boolean = false

  override def isEnum: Boolean = false

  override def getExtendsList: PsiReferenceList = this //todo: to avoid NPE from Java

  override def getImplementsList: PsiReferenceList = this //todo: to avoid NPE from Java

  override def getExtendsListTypes: Array[PsiClassType] = PsiClassType.EMPTY_ARRAY

  override def getImplementsListTypes: Array[PsiClassType] = PsiClassType.EMPTY_ARRAY

  override def getSuperClass: PsiClass = null

  override def getInterfaces: Array[PsiClass] = PsiClass.EMPTY_ARRAY

  override def getSupers: Array[PsiClass] = PsiClass.EMPTY_ARRAY

  override def getSuperTypes: Array[PsiClassType] = PsiClassType.EMPTY_ARRAY

  override def psiFields: Array[PsiField] = PsiField.EMPTY_ARRAY // todo

  override def getConstructors: Array[PsiMethod] = PsiMethod.EMPTY_ARRAY // todo

  override def psiInnerClasses: Array[PsiClass] = PsiClass.EMPTY_ARRAY // todo

  override def getInitializers: Array[PsiClassInitializer] = PsiClassInitializer.EMPTY_ARRAY

  override def getAllFields: Array[PsiField] = getFields

  override def getAllMethods: Array[PsiMethod] = getMethods

  override def getAllInnerClasses: Array[PsiClass] = getInnerClasses

  override def findFieldByName(name: String, checkBases: Boolean): PsiField = null

  override def findMethodBySignature(patternMethod: PsiMethod, checkBases: Boolean): PsiMethod = null

  override def findMethodsBySignature(patternMethod: PsiMethod, checkBases: Boolean): Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  override def findMethodsAndTheirSubstitutorsByName(name: String, checkBases: Boolean): List[Pair[PsiMethod, PsiSubstitutor]] = Collections.emptyList[Pair[PsiMethod, PsiSubstitutor]]

  override def getAllMethodsAndTheirSubstitutors: List[Pair[PsiMethod, PsiSubstitutor]] = Collections.emptyList[Pair[PsiMethod, PsiSubstitutor]]

  override def findInnerClassByName(name: String, checkBases: Boolean): PsiClass = null

  override def getLBrace: PsiJavaToken = null

  override def getRBrace: PsiJavaToken = null

  override def getScope: PsiElement = null

  override def isInheritor(baseClass: PsiClass, checkDeep: Boolean): Boolean = false

  override def isInheritorDeep(baseClass: PsiClass, classToPass: PsiClass): Boolean = false

  override def getVisibleSignatures: Collection[HierarchicalMethodSignature] = Collections.emptyList[HierarchicalMethodSignature]

  override def getModifierList: PsiModifierList = ScalaPsiUtil.getEmptyModifierList(getManager)

  override def hasModifierProperty(name: String): Boolean = name.equals(PsiModifier.PUBLIC)

  override def hasTypeParameters: Boolean = false

  override def getTypeParameterList: PsiTypeParameterList = null

  override def psiTypeParameters: Array[PsiTypeParameter] = PsiTypeParameter.EMPTY_ARRAY

  override def findMethodsByName(name: String, checkBases: Boolean): Array[PsiMethod] = Array[PsiMethod]()

  override def psiMethods: Array[PsiMethod] = Array[PsiMethod]()

  override def getQualifiedName: String = null

  override def getContainingClass: PsiClass = null
}