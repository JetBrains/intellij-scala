package org.jetbrains.plugins.scala.lang.psi.fake

import com.intellij.lang.Language
import com.intellij.psi._
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.impl.source.HierarchicalMethodSignatureImpl
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.{MethodSignatureBackedByPsiMethod, PsiTreeUtil}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.adapters.{PsiAnnotatedAdapter, PsiTypeParametersOwnerAdapter}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result._

import java.util
import javax.swing.Icon

abstract class FakePsiMethod(navElement: PsiElement,
                             psiMember: Option[PsiMember],
                             name: String) extends LightElement(navElement.getManager, navElement.getLanguage) with PsiMethod with PsiTypeParametersOwnerAdapter {

  def params: Array[Parameter]

  def retType: ScType

  override def toString: String = name + "()"

  override def getContainingClass: PsiClass = PsiTreeUtil.getParentOfType(navElement, classOf[ScTypeDefinition])

  override def getTextOffset: Int = navElement.getTextOffset

  override def getNavigationElement: PsiElement = navElement

  override def getOriginalElement: PsiElement = navElement

  override def getDocComment: PsiDocComment = null

  override def isDeprecated: Boolean = false

  //noinspection ScalaWrongMethodsUsage
  override def hasModifierProperty(name: String): Boolean = psiMember.exists(_.hasModifierProperty(name))

  override def getTypeParameterList: PsiTypeParameterList = null

  override def psiTypeParameters: Array[PsiTypeParameter] = PsiTypeParameter.EMPTY_ARRAY

  override def hasTypeParameters: Boolean = false

  override def copy: PsiElement = new FakePsiMethod(navElement, psiMember, name) {
    override def params: Array[Parameter] = FakePsiMethod.this.params
    override def retType: ScType = FakePsiMethod.this.retType
  }

  override def getParameterList: PsiParameterList = new FakePsiParameterList(getManager, getLanguage) {
    override def params: Array[Parameter] = FakePsiMethod.this.params
  }

  override def findDeepestSuperMethod: PsiMethod = null

  override def getSignature(substitutor: PsiSubstitutor): MethodSignatureBackedByPsiMethod = {
    MethodSignatureBackedByPsiMethod.create(this, substitutor)
  }

  override def findSuperMethodSignaturesIncludingStatic(checkAccess: Boolean): util.List[MethodSignatureBackedByPsiMethod] = null

  override def findSuperMethods(checkAccess: Boolean): Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  override def setName(name: String): PsiElement = new FakePsiMethod(navElement, psiMember, name) {
    override def params: Array[Parameter] = FakePsiMethod.this.params
    override def retType: ScType = FakePsiMethod.this.retType
  }

  override def getModifierList: PsiModifierList = navElement match {
    case b: ScBindingPattern =>
      b.nameContext match {
        case v: ScVariable => v.getModifierList
        case v: ScValue => v.getModifierList
        case _ => ScalaPsiUtil.getEmptyModifierList(getManager)
      }
    case _ => ScalaPsiUtil.getEmptyModifierList(getManager)
  }

  override def findSuperMethods(parentClass: PsiClass): Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  override def getBody: PsiCodeBlock = null

  override def findSuperMethods: Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  override def getReturnType: PsiType = retType.toPsiType

  override def findDeepestSuperMethods: Array[PsiMethod] = PsiMethod.EMPTY_ARRAY

  override def isConstructor: Boolean = false

  override def getThrowsList: PsiReferenceList = new FakePsiReferenceList(getManager, getLanguage, PsiReferenceList.Role.THROWS_LIST)

  override def isVarArgs: Boolean = false

  override def getIcon(flags: Int): Icon = navElement match {
    case t: ScTypedDefinition =>
      val context = t.nameContext
      if (context != null) context.getIcon(flags)
      else super.getIcon(flags)
    case _ => super.getIcon(flags)
  }

  override def getReturnTypeElement: PsiTypeElement = null

  override def getHierarchicalMethodSignature: HierarchicalMethodSignature =
    new HierarchicalMethodSignatureImpl(getSignature(PsiSubstitutor.EMPTY))

  override def getName: String = name

  override def getNameIdentifier: PsiIdentifier = null
}

object FakePsiMethod {
  def unapply(method: FakePsiMethod): Option[PsiElement] = Some(method.getOriginalElement)

  def getter(t: ScTypedDefinition, name: String): FakePsiMethod =
    new FakePsiMethod(t, t.nameContext.asOptionOf[PsiMember], name) {

      override def params: Array[Parameter] = Array.empty

      override def retType: ScType = t.`type`().getOrAny
    }

  def setter(t: ScTypedDefinition, name: String): FakePsiMethod =
    new FakePsiMethod(t, t.nameContext.asOptionOf[PsiMember], name) {

      override def params: Array[Parameter] = Array(Parameter(t.`type`().getOrAny, isRepeated = false, index = 0))

      override def retType: ScType = api.Unit(t.projectContext)
    }
}

class FakePsiTypeElement(manager: PsiManager, language: Language, tp: ScType)
        extends LightElement(manager, language) with PsiTypeElement with PsiAnnotatedAdapter {

  override def getInnermostComponentReferenceElement: PsiJavaCodeReferenceElement = null

  override def getType: PsiType = tp.toPsiType

  override def addAnnotation(qualifiedName: String): PsiAnnotation = null

  override def findAnnotation(qualifiedName: String): PsiAnnotation = null

  override def getApplicableAnnotations: Array[PsiAnnotation] = PsiAnnotation.EMPTY_ARRAY

  override def psiAnnotations: Array[PsiAnnotation] = PsiAnnotation.EMPTY_ARRAY

  override def getText: String = tp.toString

  override def toString: String = tp.toString

  override def copy: PsiElement = new FakePsiTypeElement(manager, language, tp)
}

abstract class FakePsiParameter(manager: PsiManager, language: Language, name: String)
        extends LightElement(manager, language) with PsiParameter {

  def parameter: Parameter

  override def getDeclarationScope: PsiElement = null

  override def setName(name: String): PsiElement = this //do nothing

  override def getNameIdentifier: PsiIdentifier = null

  override def computeConstantValue: AnyRef = null

  override def normalizeDeclaration(): Unit = {}

  override def hasInitializer: Boolean = false

  override def getInitializer: PsiExpression = null

  override def getType: PsiType = parameter.paramType.toPsiType

  override def isVarArgs: Boolean = false

  override def getName: String = name

  override def copy: PsiElement = new FakePsiParameter(manager, language, name) {
    override def parameter: Parameter = FakePsiParameter.this.parameter
  }

  override def getText: String = "param: " + getTypeElement.getText

  override def toString: String = getText

  override def getModifierList: PsiModifierList = ScalaPsiUtil.getEmptyModifierList(getManager)

  override def hasModifierProperty(name: String): Boolean = false

  override def getTypeElement: PsiTypeElement = new FakePsiTypeElement(manager, language, parameter.paramType)
}

abstract class FakePsiParameterList(manager: PsiManager, language: Language)
        extends LightElement(manager, language) with PsiParameterList {

  def params: Array[Parameter]

  override def getParameters: Array[PsiParameter] = params.map(p =>
    new FakePsiParameter(manager, language, "param") {
      override def parameter: Parameter = p
    })

  override def getParametersCount: Int = params.length

  override def getParameterIndex(parameter: PsiParameter): Int = getParameters.indexWhere(_ == parameter)

  override def getText: String = getParameters.map(_.getText).mkString("(", ", ", ")")

  override def toString: String = "FakePsiParameterList"

  override def copy: PsiElement = new FakePsiParameterList(manager, language) {
    override def params: Array[Parameter] = FakePsiParameterList.this.params
  }
}

class FakePsiReferenceList(manager: PsiManager, language: Language, role: PsiReferenceList.Role) extends LightElement(manager, language) with PsiReferenceList {
  override def getText: String = ""

  override def getRole: PsiReferenceList.Role = role

  override def getReferenceElements: Array[PsiJavaCodeReferenceElement] = PsiJavaCodeReferenceElement.EMPTY_ARRAY

  override def getReferencedTypes: Array[PsiClassType] = PsiClassType.EMPTY_ARRAY

  override def toString: String = ""

  override def copy: PsiElement = new FakePsiReferenceList(manager, language, role)
}

class FakePsiTypeParameterList(manager: PsiManager, language: Language, params: Array[ScTypeParam], owner: PsiTypeParameterListOwner)
        extends LightElement(manager, language) with PsiTypeParameterList {
  override def getText: String = params.map(_.getText).mkString("(", ", ", ")")

  override def toString: String = "FakePsiTypeParameterList"

  override def copy: PsiElement = new FakePsiTypeParameterList(manager, language, params, owner)

  override def getTypeParameterIndex(typeParameter: PsiTypeParameter): Int = getTypeParameters.indexOf(typeParameter)

  override val getTypeParameters: Array[PsiTypeParameter] = params.map(a => a)
}

