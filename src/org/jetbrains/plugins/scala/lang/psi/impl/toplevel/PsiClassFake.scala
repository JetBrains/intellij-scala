package org.jetbrains.plugins.scala.lang.psi.impl.toplevel

import api.base.ScModifierList
import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, PsiClass}
import com.intellij.psi.tree._
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.editor.colors._

import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.lexer._
import psi.api.toplevel.packaging._
import psi.api.toplevel.templates._

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.psi._;
import com.intellij.navigation._;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.util.IncorrectOperationException;

import _root_.java.util.Collection;
import _root_.java.util.Collections;
import _root_.java.util.List;


/**
 * @author ilyas
 */

trait PsiClassFake extends PsiClass{

  def isInterface: Boolean = false

  def isAnnotationType: Boolean = false

  def isEnum: Boolean = false

  def getExtendsList: PsiReferenceList = null

  def getImplementsList: PsiReferenceList = null

  def getExtendsListTypes: Array[PsiClassType] = PsiClassType.EMPTY_ARRAY

  def getImplementsListTypes: Array[PsiClassType] = PsiClassType.EMPTY_ARRAY

  def getSuperClass: PsiClass = null

  def getInterfaces: Array[PsiClass] = PsiClass.EMPTY_ARRAY

  def getSupers: Array[PsiClass] = PsiClass.EMPTY_ARRAY

  def getSuperTypes: Array[PsiClassType] = PsiClassType.EMPTY_ARRAY

  def getFields: Array[PsiField] = PsiField.EMPTY_ARRAY // todo

  def getConstructors: Array[PsiMethod] = PsiMethod.EMPTY_ARRAY // todo

  def getInnerClasses: Array[PsiClass] = PsiClass.EMPTY_ARRAY // todo

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

  def getModifierList: ScModifierList = null

  def hasModifierProperty(name: String): Boolean = name.equals(PsiModifier.PUBLIC)

  def getDocComment: PsiDocComment = null

  def isDeprecated: Boolean = false

  def getMetaData: PsiMetaData = null

  def isMetaEnough: Boolean = false

  def hasTypeParameters: Boolean = false

  def getTypeParameterList: PsiTypeParameterList = null

  def getTypeParameters: Array[PsiTypeParameter] = PsiTypeParameter.EMPTY_ARRAY

  def findMethodsByName(name: String, checkBases: Boolean): Array[PsiMethod] = Array[PsiMethod]()

  def getMethods() = Array[PsiMethod]()

  def getQualifiedName() : String = null

  def getContainingClass() : PsiClass = null
}