package org.jetbrains.plugins.scala.lang.psi.javaView

import com.intellij.psi._
import com.intellij.psi.meta.PsiMetaData
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.openapi.util.Pair
import com.intellij.util.IncorrectOperationException
import com.intellij.navigation.NavigationItem
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.RowIcon;
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.psi._

import java.util.List
import java.util.Collection
import java.util.Collections
import javax.swing._

import psi.api.toplevel.typedef._

/**
* @author ven
*/
case class ScJavaClass(scClass: ScTypeDefinition, parent: PsiElement) extends ScJavaElement(scClass, parent) with PsiClass {

  def getQualifiedName: String = scClass.getQualifiedName

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

  def getMethods: Array[PsiMethod] = PsiMethod.EMPTY_ARRAY // todo

  def getConstructors: Array[PsiMethod] = PsiMethod.EMPTY_ARRAY // todo

  def getInnerClasses: Array[PsiClass] = PsiClass.EMPTY_ARRAY // todo

  def getInitializers: Array[PsiClassInitializer] = PsiClassInitializer.EMPTY_ARRAY

  def getAllFields: Array[PsiField] = getFields

  def getAllMethods: Array[PsiMethod] = getMethods

  def getAllInnerClasses: Array[PsiClass] = getInnerClasses

  def findFieldByName(name: String, checkBases: Boolean): PsiField = null

  def findMethodBySignature(patternMethod: PsiMethod, checkBases: Boolean): PsiMethod = null

  def findMethodsBySignature(patternMethod: PsiMethod, checkBases: Boolean): Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  def findMethodsByName(name: String, checkBases: Boolean): Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  def findMethodsAndTheirSubstitutorsByName(nmae: String, checkBases: Boolean): List[Pair[PsiMethod, PsiSubstitutor]] = Collections.emptyList[Pair[PsiMethod, PsiSubstitutor]]

  def findMethodsAndTheirSubstitutors: List[Pair[PsiMethod, PsiSubstitutor]] = Collections.emptyList[Pair[PsiMethod, PsiSubstitutor]]

  def getAllMethodsAndTheirSubstitutors: List[Pair[PsiMethod, PsiSubstitutor]] = Collections.emptyList[Pair[PsiMethod, PsiSubstitutor]]

  def findInnerClassByName(name: String, checkBases: Boolean): PsiClass = null

  def getLBrace: PsiJavaToken = null

  def getRBrace: PsiJavaToken = null

  def getNameIdentifier: PsiIdentifier = null

  def getScope: PsiElement = null

  def isInheritor(baseClass: PsiClass, checkDeep: Boolean): Boolean = false

  def isInheritorDeep(baseClass: PsiClass, classToPass: PsiClass): Boolean = false

//  def getPom: PomMemberOwner = null

  def getContainingClass: PsiClass = getParent match {
    case clazz: PsiClass => clazz.asInstanceOf[PsiClass]
    case _ => null
  }

  def getVisibleSignatures: Collection[HierarchicalMethodSignature] = Collections.emptyList[HierarchicalMethodSignature]

  def getModifierList: PsiModifierList = null

  def hasModifierProperty(name: String): Boolean = name.equals(PsiModifier.PUBLIC)

  def getDocComment: PsiDocComment = null

  def isDeprecated: Boolean = false

  def getMetaData: PsiMetaData = null

  def isMetaEnough: Boolean = false

  def hasTypeParameters: Boolean = false

  def getTypeParameterList: PsiTypeParameterList = null

  def getTypeParameters: Array[PsiTypeParameter] = PsiTypeParameter.EMPTY_ARRAY

  override def getName: String = scClass.getName

  def setName(name: String): PsiElement = this //todo

  def getChildren: Array[PsiElement] = PsiElement.EMPTY_ARRAY

  def getPath: String = {
    var qualName = getQualifiedName;
    val index = qualName.lastIndexOf('.');
    if (index < 0 || index >= (qualName.length() - 1))
      ""
    else
      qualName.substring(0, index);
  }

  override def getPresentation(): ItemPresentation = {
    new ItemPresentation() {

      import org.jetbrains.plugins.scala._
      import org.jetbrains.plugins.scala.icons._

      def getPresentableText(): String = {
        getName
      }
      override def getTextAttributesKey(): TextAttributesKey = null
      override def getLocationString(): String = getPath match {
        case "" => ""
        case _  => '(' + getPath + ')'
      }
      override def getIcon(open: Boolean): Icon = scClass.getIcon(0)
    }
  }


  /**
  *  May be class, trait or object...
  */
  def getClassInstance = scClass

  override def getIcon(flags : Int) = scClass.getIcon(flags)

  override def navigate(requestFocus: Boolean): Unit = scClass.navigate(requestFocus)

  override def canNavigate(): Boolean = scClass.canNavigate()

  override def canNavigateToSource(): Boolean = scClass.canNavigateToSource()
}
