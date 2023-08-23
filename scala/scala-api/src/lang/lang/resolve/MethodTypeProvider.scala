package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiMethodExt, PsiParameterExt, PsiTypeExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameterClause, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFun, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}

import scala.annotation.tailrec
import scala.language.implicitConversions

sealed trait MethodTypeProvider[+T <: PsiElement] {
  protected def element: T

  protected implicit def scope: ElementScope = ElementScope(element)

  def methodType(returnType: Option[ScType] = None): ScType

  def typeParameters: Seq[PsiTypeParameter]

  /**
    * Returns internal type with type parameters.
    */
  def polymorphicType(
    s:                    ScSubstitutor  = ScSubstitutor.empty,
    returnType:           Option[ScType] = None,
    dropExtensionClauses: Boolean        = false
  ): ScType = {
    val typeParams = typeParameters
    val mTpe = methodType(returnType)

    val tpe =
      if (typeParams.isEmpty) mTpe
      else                    ScTypePolymorphicType(mTpe, typeParams.map(TypeParameter(_)))

    s(tpe)
  }

}

trait ScalaMethodTypeProvider[+T <: ScalaPsiElement] extends MethodTypeProvider[T] {
  def nestedMethodType(
    n:           Int,
    `type`:      Option[ScType] = None,
    substitutor: ScSubstitutor  = ScSubstitutor.empty
  ): Option[ScType] =
    nested(methodType(`type`), n)
      .map(substitutor)

  /**
    * Unwraps the method type corresponding to the parameter section at index `n`.
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

  protected final def constructMethodType(rtpe: ScType, clauses: Seq[ScParameterClause]): ScType =
    if (clauses.nonEmpty)
      clauses.foldRight[ScType](rtpe) { (clause: ScParameterClause, tp: ScType) =>
        ScMethodType(tp, clause.getSmartParameters, clause.isImplicitOrUsing)
      }
    else ScMethodType(rtpe, Seq.empty, isImplicit = false)
}

object MethodTypeProvider {

  implicit def fromScFun(f: ScFun): ScalaMethodTypeProvider[ScFun] =
    ScFunProvider(f)

  implicit def fromScMethodLike(ml: ScMethodLike): ScalaMethodTypeProvider[ScMethodLike] = ml match {
    case f: ScFunction            => ScFunctionProvider(f)
    case pc: ScPrimaryConstructor => ScPrimaryConstructorProvider(pc)
  }

  implicit class PsiMethodTypeProviderExt(private val m: PsiMethod) extends AnyVal {
    def methodTypeProvider(scope: ElementScope): MethodTypeProvider[PsiMethod] = m match {
      case ml: ScMethodLike => fromScMethodLike(ml)
      case m: PsiMethod     => JavaMethodProvider(m)(scope)
    }
  }

  private case class ScFunProvider(override val element: ScFun)
    extends ScalaMethodTypeProvider[ScFun] {

    override def typeParameters: Seq[PsiTypeParameter] = element.typeParameters

    override def methodType(returnType: Option[ScType]): ScType = {
      val retType = returnType.getOrElse(element.retType)
      element.paramClauses.foldRight(retType) {
        case (params, tp) => ScMethodType(tp, params, isImplicit = false)
      }
    }
  }

  private case class ScFunctionProvider(override val element: ScFunction)
    extends ScalaMethodTypeProvider[ScFunction] {

    override def typeParameters: Seq[PsiTypeParameter] =
      element match {
        case AuxiliaryConstructor.in(td: ScTypeDefinition) => td.typeParameters
        case _                                             => element.typeParameters
      }

    override def methodType(returnType: Option[ScType]): ScType = {
      val retType =
        returnType.getOrElse {
          element match {
            case ScalaConstructor.in(tdef: ScTypeDefinition) => tdef.`type`().getOrAny
            case _                                           => element.returnType.getOrAny
          }
        }
      // TODO: it looks not OK that we return the return type instead of ScMethodType
      //  e.g we have following results
      //  def f0: String           = ???   //type: String
      //  def f00(): String        = ???   //type: () => String
      //  def f1(int: Int): String = ???   //type: Int => String
      //  Though REPL will return `() => String` for both `f0 _` and `f00 _`
      //  looks like for empty parameter clauses we also might return () => String with some special mark "no clauses"
      if (!element.hasParameterClause)
        return retType

      val clauses = element.effectiveParameterClauses
      constructMethodType(retType, clauses)
    }

    override def polymorphicType(
      s:                    ScSubstitutor,
      returnType:           Option[ScType],
      dropExtensionClauses: Boolean
    ): ScType = {
      val regularMethodResult = super.polymorphicType(s, returnType)

      if (dropExtensionClauses) regularMethodResult
      else {
        /**
         * If this is an extension method, its type would be
         * `[A1, B1, C1] => Foo => using Bar => [A2, B2, C2] => Qux => using Quux`,
         * where extension type and value parameter sections are prepended to the
         * actual method type.
         */
        element.extensionMethodOwner.fold(regularMethodResult) { ext =>
          val extTypeParams = ext.typeParameters
          val extParams     = ext.effectiveParameterClauses

          val newMethodType = s(constructMethodType(regularMethodResult, extParams))

          if (extTypeParams.nonEmpty) ScTypePolymorphicType(newMethodType, extTypeParams.map(TypeParameter(_)))
          else                        newMethodType
        }
      }
    }
  }

  private case class ScPrimaryConstructorProvider(override val element: ScPrimaryConstructor)
    extends ScalaMethodTypeProvider[ScPrimaryConstructor] {

    override def typeParameters: Seq[PsiTypeParameter] = element.containingClass.typeParameters

    override def methodType(returnType: Option[ScType]): ScType = {
      val parameters: ScParameters = element.parameterList
      val retType: ScType = returnType.getOrElse(containingClassType)

      val clauses = parameters.clauses
      if (clauses.isEmpty) return ScMethodType(retType, Seq.empty, isImplicit = false)

      clauses.foldRight[ScType](retType) { (clause: ScParameterClause, tp: ScType) =>
        ScMethodType(tp, clause.getSmartParameters, clause.isImplicitOrUsing)
      }
    }

    private def containingClassType: ScType = element.containingClass.`type`().getOrAny
  }

  private case class JavaMethodProvider(override val element: PsiMethod)
                                       (override implicit val scope: ElementScope)
    extends MethodTypeProvider[PsiMethod] {

    override def typeParameters: Seq[PsiTypeParameter] = {
      val clsTypeParameters =
        if (element.isConstructor)
          element
            .getContainingClass
            .toOption
            .map(_.getTypeParameters)
            .getOrElse(Array.empty[PsiTypeParameter])
        else Array.empty[PsiTypeParameter]

      (element.getTypeParameters ++ clsTypeParameters).toSeq
    }

    override def methodType(returnType: Option[ScType] = None): ScType = {
      val retType = returnType.getOrElse(computeReturnType)
      ScMethodType(retType, parameters, isImplicit = false)
    }

    private def computeReturnType: ScType = element match {
      case f: FakePsiMethod         => f.retType
      case Constructor.ofClass(cls) => ScalaPsiUtil.constructTypeForPsiClass(cls)((tp, _) => TypeParameterType(tp))
      case _                        => element.getReturnType.toScType()
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
