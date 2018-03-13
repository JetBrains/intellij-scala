package org.jetbrains.plugins.scala
package lang
package refactoring
package util

import org.jetbrains.plugins.dotty.lang.parser.parsing.expressions.InfixExpr
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

import scala.annotation.switch


/**
 * User: Alexander Podkhalyuzin
 * Date: 30.03.2009
 */

object ScTypeUtil {

  def stripTypeArgs(tp: ScType): ScType = tp match {
    case ParameterizedType(designator, _) => designator
    case t => t
  }

  case class AliasType(ta: ScTypeAlias, lower: TypeResult, upper: TypeResult)

  val HighestPrecedence: Int = 6
  val LowestPrecedence: Int = 0

  /** Gets the precedence of a type element. Precedence can tell us
    * when parentheses are needed and when they aren't.
    *
    * E.g. with the following parse trees
    * {{{
    *   A[Int] @annot <:< B with C
    *   ------            -      -
    *     6               6      6
    *   -------------     --------
    *         5              4
    *   ----------------------------
    *                3
    * }}}
    *
    * {{{
    *   (A[Int] @annot <:< B) with C
    *    ------            -       -
    *     6                6       6
    *    -------------
    *         5
    *    -------------------
    *             3
    *   ---------------------
    *              6 <- the parentheses force precedence, so they're needed
    *   ----------------------------
    *                 4
    * }}}
    *
    *
    * The highest precedence is [[HighestPrecedence]], lowest is
    * [[LowestPrecedence]].
    *
    * @param typeElem Element for which to get the precedence
    * @return The precedence. Higher applies first.
    */
  def getPrecedence(typeElem: ScTypeElement): Int = typeElem match {
    case _: ScParameterizedTypeElement | _: ScTypeProjection | _: ScSimpleTypeElement | _: ScTupleTypeElement | _: ScParenthesisedTypeElement => 6
    case _: ScAnnotTypeElement => 5
    case _: ScCompoundTypeElement => 4
    case _: ScInfixTypeElement => 3
    case _: ScExistentialTypeElement => 2
    case _: ScWildcardTypeElement => 1
    case _: ScFunctionalTypeElement => 0
    case _ => throw new IllegalArgumentException(s"Unknown type element $typeElem")
  }


  /** Returns true if removing the parentheses from the given type element
    * would change the semantics of the parent type, according to the
    * precedence rules and associativity of type elements.
    *
    * @param parElt The parenthesised type element
    * @return True if the given type element needs to stay parenthesized,
    *         false otherwise
    */
  def typeNeedsParentheses(parElt: ScParenthesisedTypeElement): Boolean = {

    import InfixExpr.associate

    val ParElt = parElt // constant pattern
    val parent = parElt.getParent
    val childOption = parElt.typeElement

    (parent, childOption) match {
      // () is a valid type for function params
      case (_, None) => true

      case (_, Some(typeElem)) =>
        (parent, typeElem) match {
          // highest precedence
          case (_, c) if getPrecedence(c) == 6 => false

          case (p: ScTypeElement, c) if getPrecedence(p) > getPrecedence(c) => true
          case (p: ScTypeElement, c) if getPrecedence(p) < getPrecedence(c) => false

          // Infix element chain:
          // - If the two operators have different associativities, then the parentheses are required
          // - If they have the same associativity, then right- or left- associativity applies depending on the operator
          case (ScInfixTypeElement(_, ifx, _), ScInfixTypeElement(_, ifx2, _)) if associate(ifx.getText) != associate(ifx2.getText) => true

          case (ScInfixTypeElement(_, ifxOp, Some(ParElt)), _: ScInfixTypeElement) => associate(ifxOp.getText) == +1
          case (ScInfixTypeElement(ParElt, ifxOp, _), _: ScInfixTypeElement) => associate(ifxOp.getText) == -1

          // Function types are right associative, ie A => (B => C) === A => B => C
          case (ScFunctionalTypeElement(ParElt, _), _: ScFunctionalTypeElement) => true

          case _ => false
        }
    }
  }
}
