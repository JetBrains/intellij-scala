package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{IterableExt, ObjectExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

import scala.annotation.tailrec

/**
 * Derives the expected types of subexpressions from the expected type of the expression enclosing them.
 *
 * It is used both when an expression is typed against an explicitly given expected type, see
 * [[org.jetbrains.plugins.scala.lang.psi.types.result.Typeable.type]], and when the expected type of an expression
 * is computed from its context, see [[ExpectedTypesImpl]], so that the two agree.
 */
object ExpectedTypePropagation {

  /**
   * The expected type of `nested`, whose type is (a part of) the type of the enclosing expression,
   * such as the result expression of a block (SLS 6.11) or a branch of an if expression (SLS 6.16).
   *
   * An expression is converted to a context function literal, if its expected type is a context function type,
   * so `nested` is typed against the result type of that, unless it is a context function literal itself.
   */
  def nested(expectedType: ScType, nested: ScExpression): ScType =
    if (isContextFunctionLiteral(nested)) expectedType
    else                                  unwrapContextFunctionType(expectedType)

  def nested(expectedType: Option[ScType], nested: ScExpression): Option[ScType] =
    expectedType.map(this.nested(_, nested))

  /** The expected type of `result`, the result expression of `block`. */
  def blockResult(block: ScBlock, result: ScExpression, expectedType: Option[ScType]): Option[ScType] =
    block match {
      case blockExpr: ScBlockExpr =>
        if (blockExpr.needCheckExpectedType) nested(expectedType, result)
        else                                 None
      case _ if isTypedAsItsContext(block) => expectedType
      case _                               => None
    }

  /**
   * Whether a block without braces, such as the body of a case clause, is typed against
   * the expected type of the enclosing expression.
   */
  private[expr] def isTypedAsItsContext(block: ScBlock): Boolean = {
    val context = block.getContext
    context.is[ScTry, ScCaseClause, ScFunctionExpr] ||
      //extra null checks are needed for some broken code, in order not to throw NPEs
      context != null && {
        val context2 = context.getContext
        context2 != null && context2.getContext.is[ScCatchBlock]
      }
  }

  /**
   * The expected type of `result`, the body of the function literal `function` (SLS 6.23).
   *
   * @param expectedType the expected type of `function`
   */
  def functionLiteralResult(function: ScFunctionExpr, result: ScExpression, expectedType: ScType): Option[ScType] = {
    val functionLikeType = FunctionLikeType(result)

    val unwrapped =
      if (!function.isContext) unwrapContextFunctionType(expectedType)
      else                     expectedType

    unwrapped match {
      case functionLikeType(_, resultType, _) => Option(resultType)
      case _                                  => None
    }
  }

  def functionLiteralResult(function: ScFunctionExpr, result: ScExpression, expectedType: Option[ScType]): Option[ScType] =
    expectedType.flatMap(functionLiteralResult(function, result, _))

  /**
   * The expected type of `body`, the function literal of the polymorphic function literal `function`.
   *
   * @param expectedType the expected type of `function`
   */
  def polyFunctionBody(function: ScPolyFunctionExpr, body: ScExpression, expectedType: ScType): Option[ScType] =
    expectedType.removeAliasDefinitionsIn(body) match {
      case PolyFunctionType(sig, retType) =>
        val typeParams = function.typeParameters

        // match
        //   [pParams..] => bodyType
        //   [tyParams..] => ...
        // so
        // substitute bodyType[pParams.. -> tyParams..]
        val pParams = sig.typeParams.headOption.getOrElse(Seq.empty)

        val bodyType = {
          val paramTypes = sig.substitutedTypes.head.map(_.apply())
          FunctionType((retType, paramTypes))(using body.elementScope, Context.Empty)
        }

        //val f2: [T, S] => T => S => Unit = [_, _] => x => y => ???
        //Above is valid scala, which would naively be translated into
        //scala.PolyFunction { def apply[_, _](x: _): Function1[_, Unit]
        //which is not a valid scala snippet, so just introduce fresh names for
        //type parameters named _ here.
        val renameUnderscoreTypeParams =
          typeParams.mapWithIndex {
            case (tParam, idx) =>
              val renamed =
                if (tParam.name == "_") {
                  TypeParameterType(
                    TypeParameter.light(
                      s"_$$$idx",
                      tParam.typeParameters.map(TypeParameter.apply),
                      tParam.lowerBound.getOrNothing,
                      tParam.upperBound.getOrAny
                    )
                  )
                } else TypeParameterType(tParam)
              tParam -> renamed
          }.to(Map)

        val bindTypeParams = ScSubstitutor.bind(pParams, typeParams)(p => TypeParameterType(TypeParameter(p)))
        val fullSubst = bindTypeParams.followed(ScSubstitutor.bind(typeParams: Seq[ScTypeParam])(renameUnderscoreTypeParams))
        Option(fullSubst(bodyType))
      case _ => None
    }

  def polyFunctionBody(function: ScPolyFunctionExpr, body: ScExpression, expectedType: Option[ScType]): Option[ScType] =
    expectedType.flatMap(polyFunctionBody(function, body, _))

  /**
   * The expected type of `body`, the body of the case clause `clause` of a pattern matching anonymous function (SLS 8.5).
   *
   * @param expectedType the expected type of the anonymous function, see [[nested]]
   */
  def partialFunctionCaseClauseBody(clause: ScCaseClause, body: ScExpression, expectedType: ScType): Option[ScType] = {
    val functionLikeType = FunctionLikeType(body)

    expectedType match {
      case functionLikeType(_, resultType, paramTypes) =>
        val subst = clause.pattern.fold(ScSubstitutor.empty) { pattern =>
          val scrutineeType =
            if (paramTypes.size == 1) paramTypes.head
            else                      TupleType(paramTypes, context = pattern)

          PatternTypeInference.doTypeInference(pattern, scrutineeType)
        }

        Option(subst(resultType))
      case _ => None
    }
  }

  /**
   * The expected type of the body of the case clause `clause` of the match expression `matchExpr` (SLS 8.4).
   *
   * @param expectedType the expected type of `matchExpr`
   */
  def matchCaseClauseBody(matchExpr: ScMatch, clause: ScCaseClause, expectedType: ScType): ScType =
    PatternTypeInference.doForMatchClause(matchExpr, clause)(expectedType)

  def matchCaseClauseBody(matchExpr: ScMatch, clause: ScCaseClause, expectedType: Option[ScType]): Option[ScType] =
    expectedType.map(matchCaseClauseBody(matchExpr, clause, _))

  /**
   * The expected type of the component with the index `index` of a tuple of the arity `arity`.
   *
   * @param expectedType the expected type of the tuple
   */
  @tailrec
  def tupleComponent(expectedType: ScType, index: Int, arity: Int)(implicit context: Context): Option[ScType] =
    expectedType match {
      case _: ScAbstractType                       => tupleComponent(expectedType.removeAbstracts, index, arity)
      case TupleType(components) if components.length == arity => components.lift(index)
      case _                                       => None
    }

  def tupleComponent(tuple: ScTuple, index: Int, expectedType: Option[ScType]): Option[ScType] = {
    implicit val context: Context = Context(tuple)
    expectedType.flatMap(tupleComponent(_, index, tuple.exprs.length))
  }

  /**
   * The expected type of the expression of the named tuple component `component`.
   *
   * @param expectedType the expected type of the named tuple
   */
  def namedTupleComponent(component: ScNamedTupleExprComponent, expectedType: Option[ScType]): Option[ScType] = {
    implicit val context: Context = Context(component)

    val components = component.namedTuple.components
    val index      = components.indexOf(component)

    @tailrec
    def fromType(tpe: ScType): Option[ScType] = tpe match {
      case _: ScAbstractType => fromType(tpe.removeAbstracts)
      case NamedTupleType(expectedComponents) if expectedComponents.length == components.length =>
        expectedComponents(index) match {
          case (NamedTupleType.NameType(expectedName), componentType) if expectedName == component.name =>
            Option(componentType)
          case _ => None
        }
      case _ => None
    }

    if (index < 0) None
    else           expectedType.flatMap(fromType)
  }

  /**
   * The expected type of the body of an anonymous function in placeholder syntax (e.g. `_ + 1`),
   * see `fromUnderscore` in [[org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.Ext.getNonValueType]].
   *
   * @param expectedType the expected type of the anonymous function
   */
  def underscoreFunctionBody(expectedType: ScType)(implicit context: Context): Option[ScType] =
    expectedType match {
      case FunctionType(resultType, _) => Option(resultType)
      case _                           => None
    }

  def underscoreFunctionBody(place: PsiElement, expectedType: Option[ScType]): Option[ScType] = {
    implicit val context: Context = Context(place)
    expectedType.flatMap(underscoreFunctionBody)
  }

  private def isContextFunctionLiteral(expression: ScExpression): Boolean = expression match {
    case function: ScFunctionExpr => function.isContext
    case _                        => false
  }
}
