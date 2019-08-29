package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel

import java.{util => ju}

import com.intellij.openapi.util.Pair
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.adapters.PsiClassAdapter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScDocCommentOwner


/**
 * @author ilyas
 */
//noinspection TypeAnnotation
trait PsiClassFake extends PsiClassAdapter with PsiReferenceList with ScDocCommentOwner {
  //todo: this methods from PsiReferenceList to avoid NPE. It's possible for asking different roles, so we can
  //todo: have problems for simple implementation of them

  override def getRole = PsiReferenceList.Role.EXTENDS_LIST

  override def getReferencedTypes = PsiClassType.EMPTY_ARRAY

  override def getReferenceElements = PsiJavaCodeReferenceElement.EMPTY_ARRAY

  override def isInterface: Boolean = false

  override def isAnnotationType: Boolean = false

  override def isEnum: Boolean = false

  override def getExtendsList: PsiReferenceList = this //todo: to avoid NPE from Java

  override def getImplementsList: PsiReferenceList = this //todo: to avoid NPE from Java

  override def getExtendsListTypes = PsiClassType.EMPTY_ARRAY

  override def getImplementsListTypes = PsiClassType.EMPTY_ARRAY

  override def getSuperClass: PsiClass = null

  override def getInterfaces = PsiClass.EMPTY_ARRAY

  override def getSupers = PsiClass.EMPTY_ARRAY

  override def getSuperTypes = PsiClassType.EMPTY_ARRAY

  override def psiFields = PsiField.EMPTY_ARRAY // todo

  override def getConstructors = PsiMethod.EMPTY_ARRAY // todo

  override def psiInnerClasses = PsiClass.EMPTY_ARRAY // todo

  override def getInitializers = PsiClassInitializer.EMPTY_ARRAY

  override def getAllFields = getFields

  override def getAllMethods = getMethods

  override def getAllInnerClasses = getInnerClasses

  override def findFieldByName(name: String, checkBases: Boolean): PsiField = null

  override def findMethodBySignature(patternMethod: PsiMethod, checkBases: Boolean): PsiMethod = null

  override def findMethodsBySignature(patternMethod: PsiMethod, checkBases: Boolean) = PsiMethod.EMPTY_ARRAY

  override def findMethodsAndTheirSubstitutorsByName(name: String, checkBases: Boolean) = ju.Collections.emptyList[Pair[PsiMethod, PsiSubstitutor]]

  override def getAllMethodsAndTheirSubstitutors = ju.Collections.emptyList[Pair[PsiMethod, PsiSubstitutor]]

  override def findInnerClassByName(name: String, checkBases: Boolean): PsiClass = null

  override def getLBrace: PsiJavaToken = null

  override def getRBrace: PsiJavaToken = null

  override def getScope: PsiElement = null

  override def isInheritor(baseClass: PsiClass, checkDeep: Boolean): Boolean = false

  override def isInheritorDeep(baseClass: PsiClass, classToPass: PsiClass): Boolean = false

  override def getVisibleSignatures: ju.Collection[HierarchicalMethodSignature] = ju.Collections.emptyList[HierarchicalMethodSignature]

  override def getModifierList: PsiModifierList = ScalaPsiUtil.getEmptyModifierList(getManager)

  override def hasModifierProperty(name: String): Boolean = name.equals(PsiModifier.PUBLIC)

  override def hasTypeParameters: Boolean = false

  override def getTypeParameterList: PsiTypeParameterList = null

  override def psiTypeParameters = PsiTypeParameter.EMPTY_ARRAY

  override def findMethodsByName(name: String, checkBases: Boolean) = PsiMethod.EMPTY_ARRAY

  override def psiMethods = PsiMethod.EMPTY_ARRAY

  override def getQualifiedName: String = null

  override def getContainingClass: PsiClass = null
}