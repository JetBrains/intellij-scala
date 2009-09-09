package org.jetbrains.plugins.scala.lang.psi.fake

import com.intellij.psi.impl.light.LightElement
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import com.intellij.openapi.util.Key
import java.util.List
import com.intellij.psi._
import java.lang.String
import javadoc.PsiDocComment
import search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import com.intellij.openapi.project.Project
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTyped
import com.intellij.lang.Language
import util.{MethodSignatureBase, PsiTreeUtil, MethodSignatureBackedByPsiMethod, MethodSignature}

/**
 * User: Alexander Podkhalyuzin
 * Date: 07.09.2009
 */

class FakePsiMethod(
        val navElement: PsiElement,
        name: String,
        retType: ScType,
        isStatic: Boolean
        ) extends {
    val project: Project = navElement.getProject
    val scope: GlobalSearchScope = GlobalSearchScope.allScope(project)
    val manager = navElement.getManager
    val language = navElement.getLanguage
  } with LightElement(manager, language) with PsiMethod{
  def this(value: ScTyped, isStatic: Boolean) = {
    this(value, value.getName, value.calcType, isStatic)
  }
  override def toString: String = name + "()"

  def getContainingClass: PsiClass = PsiTreeUtil.getParentOfType(navElement, classOf[ScTypeDefinition])

  def getReturnTypeNoResolve: PsiType = ScType.toPsi(retType, project, scope)

  override def getTextOffset: Int = navElement.getTextOffset

  override def getNavigationElement: PsiElement = navElement

  override def getOriginalElement: PsiElement = navElement

  def getDocComment: PsiDocComment = null

  def isDeprecated: Boolean = false

  def hasModifierProperty(name: String): Boolean = {
    name match {
      case "static" if isStatic => true
      case _ => false
    }
  }

  def getTypeParameterList: PsiTypeParameterList = null

  def getTypeParameters: Array[PsiTypeParameter] = PsiTypeParameter.EMPTY_ARRAY

  def hasTypeParameters: Boolean = false

  def accept(visitor: PsiElementVisitor): Unit = {}

  def copy: PsiElement = new FakePsiMethod(navElement, name, retType, isStatic)

  def getText: String = throw new IncorrectOperationException

  def getParameterList: PsiParameterList = new FakePsiParameterList(manager, language)

  def findDeepestSuperMethod: PsiMethod = null

  def getSignature(substitutor: PsiSubstitutor): MethodSignature = {
    new MethodSignatureBase(PsiSubstitutor.EMPTY, PsiType.EMPTY_ARRAY, PsiTypeParameter.EMPTY_ARRAY) {
      def isRaw: Boolean = false

      def getName: String = name
    }
  }

  def findSuperMethodSignaturesIncludingStatic(checkAccess: Boolean): List[MethodSignatureBackedByPsiMethod] = null

  def findSuperMethods(checkAccess: Boolean): Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  def setName(name: String): PsiElement = throw new IncorrectOperationException

  def getModifierList: PsiModifierList = null

  def findSuperMethods(parentClass: PsiClass): Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  def getBody: PsiCodeBlock = null

  def findSuperMethods: Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  def getReturnType: PsiType = ScType.toPsi(retType, project, scope)

  def findDeepestSuperMethods: Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  def isConstructor: Boolean = false

  def getThrowsList: PsiReferenceList = new FakePsiReferenceList(manager, language, PsiReferenceList.Role.THROWS_LIST)

  def isVarArgs: Boolean = false

  def getReturnTypeElement: PsiTypeElement = null

  def getHierarchicalMethodSignature: HierarchicalMethodSignature = null

  def getName: String = name

  def getMethodReceiver: PsiMethodReceiver = null

  def getNameIdentifier: PsiIdentifier = null
}

class FakePsiParameterList(manager: PsiManager, language: Language)
        extends LightElement(manager, language) with PsiParameterList {
  def getParameters: Array[PsiParameter] = PsiParameter.EMPTY_ARRAY

  def getParametersCount: Int = 0

  def accept(visitor: PsiElementVisitor): Unit = {}

  def getParameterIndex(parameter: PsiParameter): Int = -1

  def getText: String = "()"

  override def toString: String = "()"

  def copy: PsiElement = new FakePsiParameterList(manager, language)
}

class FakePsiReferenceList(manager: PsiManager, language: Language, role: PsiReferenceList.Role) extends LightElement(manager, language) with PsiReferenceList {
  def getText: String = ""

  def getRole: PsiReferenceList.Role = role

  def getReferenceElements: Array[PsiJavaCodeReferenceElement] = PsiJavaCodeReferenceElement.EMPTY_ARRAY

  def getReferencedTypes: Array[PsiClassType] = PsiClassType.EMPTY_ARRAY

  override def toString: String = ""

  def accept(visitor: PsiElementVisitor): Unit = {}

  def copy: PsiElement = new FakePsiReferenceList(manager, language, role)
}