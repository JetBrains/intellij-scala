package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.{PsiMethodExt, PsiParameterExt, PsiTypeExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{AuxiliaryConstructor, ScMethodLike, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameterClause, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFun, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType}
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}

import scala.annotation.tailrec
import scala.collection.Seq
import scala.language.implicitConversions

/**
  * Nikolay.Tropin
  * 23-Dec-17
  */
sealed trait MethodTypeProvider[+T <: PsiElement] {
  protected def element: T

  protected implicit def scope: ElementScope = ElementScope(element)

  def methodType(returnType: Option[ScType] = None): ScType

  def typeParameters: Seq[PsiTypeParameter]

  /**
    * Returns internal type with type parameters.
    */
  def polymorphicType(s: ScSubstitutor = ScSubstitutor.empty, returnType: Option[ScType] = None): ScType = {

    val typeParams = typeParameters

    val tpe =
      if (typeParams.isEmpty) methodType(returnType)
      else ScTypePolymorphicType(methodType(returnType), typeParams.map(TypeParameter(_)))
    s(tpe)
  }
}

trait ScalaMethodTypeProvider[+T <: ScalaPsiElement] extends MethodTypeProvider[T] {

  def nestedMethodType(n: Int, `type`: Option[ScType] = None, substitutor: ScSubstitutor = ScSubstitutor.empty): Option[ScType] =
    nested(methodType(`type`), n)
      .map(substitutor)

  /**
    * Unwraps the method type corresponding to the parameter secion at index `n`.
    *
    * For example:
    *
    * def foo(a: Int)(b: String): Boolean
    *
    * nested(foo.methodType(...), 1) => MethodType(retType = Boolean, params = Seq(String))
    */
  @tailrec
  private def nested(`type`: ScType, n: Int): Option[ScType] =
    if (n == 0) Some(`type`)
    else `type` match {
      case methodType: ScMethodType => nested(methodType.result, n - 1)
      case _ => None
    }
}

object MethodTypeProvider {

  implicit def fromScFun(f: ScFun): ScalaMethodTypeProvider[ScFun] =
    ScFunProvider(f)

  implicit def fromScMethodLike(ml: ScMethodLike): ScalaMethodTypeProvider[ScMethodLike] = ml match {
    case f: ScFunction => ScFunctionProvider(f)
    case pc: ScPrimaryConstructor => ScPrimaryConstructorProvider(pc)
  }

  implicit class PsiMethodTypeProviderExt(val m: PsiMethod) extends AnyVal {
    def methodTypeProvider(scope: ElementScope): MethodTypeProvider[PsiMethod] = m match {
      case ml: ScMethodLike => fromScMethodLike(ml)
      case m: PsiMethod     => JavaMethodProvider(m)(scope)
    }
  }

  private case class ScFunProvider(element: ScFun)
    extends ScalaMethodTypeProvider[ScFun] {

    def typeParameters: Seq[PsiTypeParameter] = element.typeParameters

    def methodType(returnType: Option[ScType]): ScType = {
      val retType = returnType.getOrElse(element.retType)
      element.paramClauses.foldRight(retType) {
        case (params, tp) => ScMethodType(tp, params, isImplicit = false)
      }
    }
  }

  private case class ScFunctionProvider(element: ScFunction)
    extends ScalaMethodTypeProvider[ScFunction] {

    def typeParameters: Seq[PsiTypeParameter] = {
      element match {
        case AuxiliaryConstructor.in(td: ScTypeDefinition) => td.typeParameters
        case _ => element.typeParameters
      }
    }

    def methodType(returnType: Option[ScType]): ScType = {
      val retType = returnType.getOrElse(element.returnType.getOrAny)
      if (!element.hasParameterClause) return retType

      val clauses = element.effectiveParameterClauses
      if (clauses.nonEmpty)
        clauses.foldRight[ScType](retType) { (clause: ScParameterClause, tp: ScType) =>
          ScMethodType(tp, clause.getSmartParameters, clause.isImplicit)
        }
      else ScMethodType(retType, Seq.empty, isImplicit = false)
    }
  }

  private case class ScPrimaryConstructorProvider(element: ScPrimaryConstructor)
    extends ScalaMethodTypeProvider[ScPrimaryConstructor] {

    def typeParameters: Seq[PsiTypeParameter] = element.containingClass.typeParameters

    def methodType(returnType: Option[ScType]): ScType = {
      val parameters: ScParameters = element.parameterList
      val retType: ScType = returnType.getOrElse(containingClassType)

      val clauses = parameters.clauses
      if (clauses.isEmpty) return ScMethodType(retType, Seq.empty, isImplicit = false)

      clauses.foldRight[ScType](retType) { (clause: ScParameterClause, tp: ScType) =>
        ScMethodType(tp, clause.getSmartParameters, clause.isImplicit)
      }
    }

    private def containingClassType: ScType = {
      val clazz = element.containingClass
      val typeParameters = clazz.typeParameters
      val parentClazz = ScalaPsiUtil.getPlaceTd(clazz)
      val designatorType: ScType =
        if (parentClazz != null)
          ScProjectionType(ScThisType(parentClazz), clazz)
        else ScDesignatorType(clazz)
      if (typeParameters.isEmpty) designatorType
      else {
        ScParameterizedType(designatorType, typeParameters.map(TypeParameterType(_)))
      }
    }
  }

  private case class JavaMethodProvider(element: PsiMethod)
                                       (override implicit val scope: ElementScope)
    extends MethodTypeProvider[PsiMethod] {
    def typeParameters: Seq[PsiTypeParameter] = element.getTypeParameters

    def methodType(returnType: Option[ScType] = None): ScType = {
      val retType = returnType.getOrElse(computeReturnType)
      ScMethodType(retType, parameters, isImplicit = false)
    }

    private def computeReturnType: ScType = element match {
      case f: FakePsiMethod => f.retType
      case _ => element.getReturnType.toScType()
    }

    private def parameters: Seq[Parameter] = element match {
      case f: FakePsiMethod => f.params.toSeq
      case _ => element.parameters.map(toParameter)
    }

    private def toParameter(psiParameter: PsiParameter) = {
      val scType = psiParameter.paramType()
      Parameter(
        name = psiParameter.getName,
        deprecatedName = None,
        paramType = scType,
        expectedType = scType,
        isDefault = false,
        isRepeated = psiParameter.isVarArgs,
        isByName = false,
        index = psiParameter.index,
        psiParam = Some(psiParameter)
      )
    }
  }
}
