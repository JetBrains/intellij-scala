package org.jetbrains.plugins.scala.lang.psi.fake

import java.util.List
import javax.swing.Icon

import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.impl.source.HierarchicalMethodSignatureImpl
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.{MethodSignatureBackedByPsiMethod, PsiTreeUtil}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

/**
 * User: Alexander Podkhalyuzin
 */
class FakePsiMethod(
        val navElement: PsiElement,
        name: String,
        val params: Array[Parameter],
        val retType: ScType,
        hasModifier: String => Boolean
        ) extends {
    val project: Project = navElement.getProject
    val scope: GlobalSearchScope = navElement.getResolveScope
    val manager = navElement.getManager
    val language = navElement.getLanguage
  } with LightElement(manager, language) with PsiMethod {
  def this(value: ScTypedDefinition, hasModifier: String => Boolean) = {
    this(value, value.name, Array.empty, value.getType(TypingContext.empty).getOrAny, hasModifier)
  }
  override def toString: String = name + "()"

  def getContainingClass: PsiClass = PsiTreeUtil.getParentOfType(navElement, classOf[ScTypeDefinition])

  def getReturnTypeNoResolve: PsiType = retType.toPsiType(project, scope)

  override def getTextOffset: Int = navElement.getTextOffset

  override def getNavigationElement: PsiElement = navElement

  override def getOriginalElement: PsiElement = navElement

  def getDocComment: PsiDocComment = null

  def isDeprecated: Boolean = false

  def hasModifierProperty(name: String): Boolean = {
    hasModifier(name)
  }

  def isExtensionMethod: Boolean = false

  def getTypeParameterList: PsiTypeParameterList = null

  def getTypeParameters: Array[PsiTypeParameter] = PsiTypeParameter.EMPTY_ARRAY

  def hasTypeParameters: Boolean = false

  override def copy: PsiElement = new FakePsiMethod(navElement, name, params, retType, hasModifier)

  def getParameterList: PsiParameterList = new FakePsiParameterList(manager, language, params)

  def findDeepestSuperMethod: PsiMethod = null

  def getSignature(substitutor: PsiSubstitutor): MethodSignatureBackedByPsiMethod = {
    MethodSignatureBackedByPsiMethod.create(this, substitutor)/*
    new MethodSignatureBase(PsiSubstitutor.EMPTY, getParameterList.getParameters.map(_.getType), PsiTypeParameter.EMPTY_ARRAY) {
      def isRaw: Boolean = false

      def getName: String = name
    }*/
  }

  def findSuperMethodSignaturesIncludingStatic(checkAccess: Boolean): List[MethodSignatureBackedByPsiMethod] = null

  def findSuperMethods(checkAccess: Boolean): Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  def setName(name: String): PsiElement = new FakePsiMethod(navElement, name, params, retType, hasModifier)

  def getModifierList: PsiModifierList = navElement match {
    case b: ScBindingPattern =>
      b.nameContext match {
        case v: ScVariable => v.getModifierList
        case v: ScValue => v.getModifierList
        case _ => ScalaPsiUtil.getEmptyModifierList(getManager)
      }
    case _ => ScalaPsiUtil.getEmptyModifierList(getManager)
  }

  def findSuperMethods(parentClass: PsiClass): Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  def getBody: PsiCodeBlock = null

  def findSuperMethods: Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  def getReturnType: PsiType = retType.toPsiType(project, scope)

  def findDeepestSuperMethods: Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  def isConstructor: Boolean = false

  def getThrowsList: PsiReferenceList = new FakePsiReferenceList(manager, language, PsiReferenceList.Role.THROWS_LIST)

  def isVarArgs: Boolean = false

  override def getIcon(flags: Int): Icon = navElement match {
    case t: ScTypedDefinition =>
      val context = t.nameContext
      if (context != null) context.getIcon(flags)
      else super.getIcon(flags)
    case _ => super.getIcon(flags)
  }

  def getReturnTypeElement: PsiTypeElement = null

  def getHierarchicalMethodSignature: HierarchicalMethodSignature =
    new HierarchicalMethodSignatureImpl(getSignature(PsiSubstitutor.EMPTY))

  override def getName: String = name

  def getNameIdentifier: PsiIdentifier = null
}

class FakePsiTypeElement(manager: PsiManager, language: Language, tp: ScType)
        extends LightElement(manager, language) with PsiTypeElement {
  def getTypeNoResolve(context: PsiElement): PsiType = PsiType.VOID //ScType.toPsi(tp, manager.getProject, GlobalSearchScope.allScope(manager.getProject))

  def getOwner(annotation: PsiAnnotation): PsiAnnotationOwner = null

  def getInnermostComponentReferenceElement: PsiJavaCodeReferenceElement = null

  def getType: PsiType = tp.toPsiType(manager.getProject, GlobalSearchScope.allScope(manager.getProject))

  def addAnnotation(qualifiedName: String): PsiAnnotation = null

  def findAnnotation(qualifiedName: String): PsiAnnotation = null

  def getApplicableAnnotations: Array[PsiAnnotation] = PsiAnnotation.EMPTY_ARRAY

  def getAnnotations: Array[PsiAnnotation] = PsiAnnotation.EMPTY_ARRAY

  override def getText: String = tp.toString

  override def toString: String = tp.toString

  override def copy: PsiElement = new FakePsiTypeElement(manager, language, tp)
}

class FakePsiParameter(manager: PsiManager, language: Language, val parameter: Parameter, name: String)
        extends LightElement(manager, language) with PsiParameter {
  def getDeclarationScope: PsiElement = null

  def getTypeNoResolve: PsiType = PsiType.VOID

  def setName(name: String): PsiElement = this //do nothing

  def getNameIdentifier: PsiIdentifier = null

  def computeConstantValue: AnyRef = null

  def normalizeDeclaration() {}

  def hasInitializer: Boolean = false

  def getInitializer: PsiExpression = null

  def getType: PsiType = parameter.paramType.toPsiType(manager.getProject, GlobalSearchScope.allScope(manager.getProject))

  def isVarArgs: Boolean = false

  def getAnnotations: Array[PsiAnnotation] = PsiAnnotation.EMPTY_ARRAY

  override def getName: String = name

  override def copy: PsiElement = new FakePsiParameter(manager, language, parameter, name)

  override def getText: String = "param: " + getTypeElement.getText

  override def toString: String = getText

  def getModifierList: PsiModifierList = ScalaPsiUtil.getEmptyModifierList(getManager)

  def hasModifierProperty(name: String): Boolean = false

  def getTypeElement: PsiTypeElement = new FakePsiTypeElement(manager, language, parameter.paramType)
}

class FakePsiParameterList(manager: PsiManager, language: Language, params: Array[Parameter])
        extends LightElement(manager, language) with PsiParameterList {
  def getParameters: Array[PsiParameter] = params.map(new FakePsiParameter(manager, language, _, "param"))

  def getParametersCount: Int = params.length

  def getParameterIndex(parameter: PsiParameter): Int = getParameters.indexWhere(_ == parameter)

  override def getText: String = getParameters.map(_.getText).mkString("(", ", ", ")")

  override def toString: String = "FakePsiParameterList"

  override def copy: PsiElement = new FakePsiParameterList(manager, language, params)
}

class FakePsiReferenceList(manager: PsiManager, language: Language, role: PsiReferenceList.Role) extends LightElement(manager, language) with PsiReferenceList {
  override def getText: String = ""

  def getRole: PsiReferenceList.Role = role

  def getReferenceElements: Array[PsiJavaCodeReferenceElement] = PsiJavaCodeReferenceElement.EMPTY_ARRAY

  def getReferencedTypes: Array[PsiClassType] = PsiClassType.EMPTY_ARRAY

  override def toString: String = ""

  override def copy: PsiElement = new FakePsiReferenceList(manager, language, role)
}

class FakePsiTypeParameterList(manager: PsiManager, language: Language, params: Array[ScTypeParam], owner: PsiTypeParameterListOwner)
        extends LightElement(manager, language) with PsiTypeParameterList {
  override def getText: String = params.map(_.getText).mkString("(", ", ", ")")

  override def toString: String = "FakePsiTypeParameterList"

  override def copy: PsiElement = new FakePsiTypeParameterList(manager, language, params, owner)

  def getTypeParameterIndex(typeParameter: PsiTypeParameter): Int = getTypeParameters.indexOf(typeParameter)

  val getTypeParameters: Array[PsiTypeParameter] = params.map(a => a)
}

