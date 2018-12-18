package org.jetbrains.plugins.scala.util

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScGenericCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScalaType}
import org.jetbrains.plugins.scala.lang.psi.types.api.Any
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * Partially based on and inspired by contribution from @vovapolu
  * (https://github.com/JetBrains/intellij-scala/pull/435).
  */
object KindProjectorUtil {
  val Lambda: String               = "Lambda"
  val LambdaSymbolic: String       = "λ"
  val inlineSyntaxIds: Seq[String] = Seq("?", "+?", "-?")


  /**
    * Creates synhtetic companion object used in value-level polymorphic lambdas
    * (e.g. `val a: PF[List, Option] = λ[PF[List, Option]].run(_.headOption)`).
    * Apply method return type is computed in an ad-hoc manner in [[org.jetbrains.plugins.scala.lang.psi.impl.expr.ScGenericCallImpl]]
    * See usages of [[PolymorphicLambda]] extractor.
    */
  private[this] def createPolyLambdaSyntheticObject(objectName: String, place: PsiElement): ScObject = {
    val text =
      s"""
         |object $objectName {
         |  def apply[T[_[_], _[_]]]: Any = ???
         |}
       """.stripMargin

    ScalaPsiElementFactory.createObjectWithContext(text, place, place.getFirstChild)
  }

  /**
    * Synthetic top level declarations required to make kind-projector
    * specific syntax (e.g. `Lambda`, `λ`, `?`) resolvable.
    */
  def syntheticDeclarations(place: PsiElement): Seq[PsiElement] = {
    implicit val pc: ProjectContext = ProjectContext.fromPsi(place)

    // used in type-level lambdas
    val syntheticClasses =
      (Seq(Lambda, LambdaSymbolic) ++ inlineSyntaxIds)
        .map(new ScSyntheticClass(_, Any))

    // used in value-level lambdas
    val syntheticObjects =
      Seq(Lambda, LambdaSymbolic)
        .map(createPolyLambdaSyntheticObject(_, place))

    syntheticClasses ++ syntheticObjects
  }

  /**
    * As per kind-projector README:
    *
    *  This rewrite requires that the following are true:
    *    1) F and G are unary type constructors (i.e. of shape F[_] and G[_]).
    *    2) <expr> is an expression of type Function1[_, _].
    *    3) Op is parameterized on two unary type constructors.
    *    4) someMethod is parametric (for any type A it takes F[A] and returns G[A]).
    */
  private[this] def canBeRewritten(fn: ScFunction, tparams: Seq[ScTypeParam]): Boolean = {
    val isAbstract     = fn.isAbstractMember
    val singleArgument = fn.parameters.size == 1
    val hasTypeParam   = fn.typeParameters.size == 1

    // tparams must be two unary type constructors
    val typeParametersValid = tparams.length == 2 && tparams.forall(_.typeParameters.size == 1)

    def isAppliedTypeConstructor(
      typeElement: ScTypeElement,
      cons:        ScTypeParam,
      argument:    ScTypeParam
    ): Boolean = typeElement match {
      case
        ScParameterizedTypeElement(
          ScSimpleTypeElement(Some(tcons)),
          Seq(ScSimpleTypeElement(Some(arg)))
        ) => tcons.refName == cons.name && arg.refName == argument.name
      case _ => false
    }

    isAbstract && typeParametersValid && singleArgument && hasTypeParam && {
      val correctReturnType =
        fn.returnTypeElement.exists(isAppliedTypeConstructor(_, tparams.last, fn.typeParameters.head))

      val correctParameterType =
        fn.parameters.head.typeElement.exists(isAppliedTypeConstructor(_, tparams.head, fn.typeParameters.head))

      correctReturnType && correctParameterType
    }
  }

  /**
    * Returns an intermidiate "Builder" trait which represents the type of an
    * expression of shape `Lambda[Op[F, G]]`, i.e. suppose we have the following definitions
    * {{{
    * trait Op[M[_], N[_]] {
    *   def someMethod[A](x: M[A]): N[A]
    *   def anotherMethod[B](x: M[A]): N[A]
    * }
    *
    * val a = Lambda[Op[List, Option]].someMethod(_.headOption)
    * }}}
    * A synthetic trait with the following definitions will be generated.
    * {{{
    * trait OpListOptionPolyLambdBuilder {
    *   type A
    *   def someMethod(f: List[A] => Option[A]): Op[List, Option] = ???
    *   def anotherMethod(f: List[A] => Option[A]): Op[List, Option] = ???
    * }
    * }}}
    *
    * Returns type designated to generated trait.
    */
  private[this] def kindProjectorPolymorphicLambdaType(
    target: ScTypeElement,
    lhs:    ScTypeElement,
    rhs:    ScTypeElement,
    place:  PsiElement
  )(implicit pc: ProjectContext): Option[ScType] =
    for {
      targetTpe   <- target.`type`().toOption
      targetClass <- targetTpe.extractClass
      tdef        <- targetClass.asOptionOf[ScTypeDefinition]
      tparams     = tdef.typeParameters
      methods     = tdef.functions.filter(canBeRewritten(_, tparams))
      if methods.nonEmpty
    } yield {
      val methodsText = methods.map { m =>
        val fa = s"${lhs.getText}[A]"
        val ga = s"${rhs.getText}[A]"

        s"def ${m.name}(f: $fa => $ga): ${target.getText} = ???"
      }.mkString("\n  ")

      val text =
        s"""
           |trait `${targetClass.getName}${lhs.getText}${rhs.getText}PolyLambdaBuilder` {
           |  type A
           |  $methodsText
           |}
         """.stripMargin

      val topLevel     = place.getContainingFile
      val builderTrait = ScalaPsiElementFactory.createTypeDefinitionWithContext(text, topLevel, topLevel.getFirstChild)
      ScalaType.designator(builderTrait)
    }

  object PolymorphicLambda {
    private[this] val polyLambdaIds = Seq(Lambda, LambdaSymbolic)

    def unapply(gc: ScGenericCall): Option[ScType] =
      if (gc.kindProjectorPluginEnabled) {
        implicit val pc: ProjectContext = ProjectContext.fromPsi(gc)

        gc.referencedExpr match {
          case ref: ScReferenceExpression if !ref.isQualified && polyLambdaIds.contains(ref.getText) =>
            gc.arguments match {
              case Seq(infix @ ScInfixTypeElement(lhs, _, Some(rhs)))      => kindProjectorPolymorphicLambdaType(infix, lhs, rhs, gc)
              case Seq(tpe @ ScParameterizedTypeElement(_, Seq(lhs, rhs))) => kindProjectorPolymorphicLambdaType(tpe, lhs, rhs, gc)
              case _                                                       => None
            }
          case _ => None
        }
      } else None
  }
}
