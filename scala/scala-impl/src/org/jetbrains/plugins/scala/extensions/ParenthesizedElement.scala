package org.jetbrains.plugins.scala.extensions

import com.intellij.psi.{PsiComment, PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructorInvocation, ScInfixElement, ScParenthesizedElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScParenthesisedExpr, ScSugarCallExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents

import scala.annotation.tailrec

object ParenthesizedElement {

  implicit class Ops(private val parenthesized: ScParenthesizedElement) extends AnyVal {

    /** Returns true if these parentheses are nested within other parentheses. */
    def isNestedParenthesis: Boolean = parenthesized.sameTreeParent.exists(_.isInstanceOf[ScParenthesizedElement])

    /** Returns true if these parentheses are directly enclosing other parentheses. */
    def isNestingParenthesis: Boolean = parenthesized.innerElement.exists(_.isInstanceOf[ScParenthesizedElement])

    /** Returns true if the parentheses represented by this node are clarifying,
      * ie they clarify precedence between operators that have different precedences.
      */
    def isParenthesisClarifying: Boolean = parenthesized match {
      case SameKindParentAndInner(_: ScSugarCallExpr, _: ScSugarCallExpr)                            => true
      case _: ScExpression                                                                           => false
      case SameKindParentAndInner(_: ScCompositePattern | _: ScNamingPattern | _: ScTuplePattern, _) => false
      case SameKindParentAndInner(p, c) if !isIndivisible(c) && !obviouslySamePriority(p, c)         => true
      case _                                                                                         => false
    }

    def getTextOfStripped(ignoreClarifying: Boolean = false): String = {
      getInnermostNonParen(parenthesized) match {
        case paren@ScParenthesizedElement(_) => paren.getText
        case elt if ignoreClarifying         => elt.getParent.getText
        case elt                             => elt.getText
      }
    }

    /** Strips parentheses from this element downwards. This node is replaced by one
      * of its subpatterns in the children of the parent node. If the
      * `keepParentheses` parameter is true, then one level of nesting is kept.
      * Otherwise, this node is replaced by its first non-parenthesized descendant.
      *
      * This method may change the semantics of the AST if the pattern
      * needed the parentheses. Use [[isParenthesisNeeded]] to ensure the parentheses aren't needed.
      *
      * @param keepOnePair Whether to keep one level of parentheses or not
      * @return The node which replaces this node. If no parentheses were
      *         found to remove, returns `this`, unmodified
      */
    def doStripParentheses(keepOnePair: Boolean = false): PsiElement = {
      getInnermostNonParen(parenthesized) match {
        case elt if keepOnePair => parenthesized.replace(elt.getParent)
        case elt                => parenthesized.replace(elt)
      }
    }

    def isParenthesisRedundant: Boolean = !isParenthesisNeeded

    /** Returns true if the parentheses represented by this node cannot be stripped
      * without changing the semantics of the AST.
      */
    def isParenthesisNeeded: Boolean = parenthesized match {
        case expr @ ScParenthesisedExpr(inner)                             => ScalaPsiUtil.needParentheses(expr, inner)
        case _ if parenthesized.innerElement.isEmpty                       => true
        case ScParenthesizedElement(inner) if containsSomethingElse(inner) => true
        case _ if isFunctionTupleParameter                                 => true
        case SameKindParentAndInner(parent, inner)                         => !parenthesesRedundant(parent, inner)
        case ChildOf(_: ScConstructorInvocation | _: ScTemplateParents)    => true
        case _ if isIndivisibleRepeatedParamType(parenthesized)            => true
        case NeededParenthesisAroundFunctionalType()                       => true
        case _                                                             => false
      }

    def isFunctionTypeSingleParam: Boolean = parenthesized match {
      case SameKindParentAndInner(ScFunctionalTypeElement(`parenthesized`, _), _) => true
      case _ => false
    }

    private def containsSomethingElse(inner: PsiElement): Boolean = parenthesized.children.exists {
      case `inner`                                    => false
      case ElementType(ScalaTokenTypes.tLPARENTHESIS) => false
      case ElementType(ScalaTokenTypes.tRPARENTHESIS) => false
      case _: PsiWhiteSpace | _: PsiComment           => false
      case _                                          => true
    }

    //shouldn't be called on expression
    private def parenthesesRedundant[Kind <: ScParenthesizedElement#Kind](parent: Kind, inner: Kind): Boolean =
      isIndivisible(inner) ||
        getPrecedence(inner) < getPrecedence(parent) ||
        getPrecedence(inner) == getPrecedence(parent) && innerFirstAssociativity(parent, inner)

    private def isFunctionTupleParameter: Boolean =
      isFunctionTypeSingleParam && parenthesized.innerElement.exists(_.isInstanceOf[ScTupleTypeElement])

    /**Infix chain with same precedence:
      * - If the two operators have different associativities, then the parentheses are required
      * - If they have the same associativity, then right- or left- associativity applies depending on the operator
      * */
    private def innerFirstAssociativity(parent: PsiElement, inner: PsiElement): Boolean = (parent, inner) match {
      case (p: ScInfixElement, c: ScInfixElement) if p.isLeftAssoc != c.isLeftAssoc              => false

      case (ifx@ScInfixElement(_, _, right), _: ScInfixElement) if right.contains(parenthesized) => ifx.isRightAssoc
      case (ifx@ScInfixElement(`parenthesized`, _, _), _: ScInfixElement)                        => ifx.isLeftAssoc

      case (ScFunctionalTypeElement(_, Some(`parenthesized`)), _: ScFunctionalTypeElement)       => true
      case (pf: ScPolyFunctionTypeElement, _: ScFunctionalTypeElement) if pf.resultTypeElement.contains(parenthesized) => true
      case (pf: ScTypeLambdaTypeElement, _: ScTypeLambdaTypeElement | _: ScPolyFunctionTypeElement | _: ScFunctionalTypeElement) if pf.resultTypeElement.contains(parenthesized) => true
      case _                                                                                     => false
    }
  }


  /** Gets the precedence of the tree member. Lower int value is applied first (higher precedence).
    * The highest precedence is 0. Nodes with precedence 0 are indivisible.
    */
  private def getPrecedence(element: ScParenthesizedElement#Kind): Int = element match {
    case tp: ScTypeElement => typeElementPrecedence(tp)
    case pt: ScPattern     => patternPrecedence(pt)

    //todo: refactor ScalaPsiUtil.needParentheses to also use precedence
  }

  private def isIndivisible(element: ScParenthesizedElement#Kind): Boolean = getPrecedence(element) == 0

  //infix operation priority is not included to precedence of type elements,
  //but we use it to determine whether parentheses are clarifying
  private def obviouslySamePriority(parent: ScParenthesizedElement#Kind, child: ScParenthesizedElement#Kind): Boolean = {
    getPrecedence(parent) == getPrecedence(child) &&
      infixOperation(parent) == infixOperation(child)
  }

  // varies from 1 to 10
  private def infixOperationPriority(element: PsiElement) = element match {
    case infix: ScInfixElement => ParserUtils.priority(infix.operation.getText)
    case _ => 0
  }

  private def infixOperation(element: PsiElement): Option[String] =
    element.asOptionOf[ScInfixElement].map(_.operation.refName)

  private def typeElementPrecedence(te: ScTypeElement): Int = te match {
    case _: ScParameterizedTypeElement |
         _: ScTypeProjection |
         _: ScSimpleTypeElement |
         _: ScLiteralTypeElement |
         _: ScTupleTypeElement |
         _: ScParenthesisedTypeElement => 0
    case _: ScAnnotTypeElement         => 1
    case _: ScCompoundTypeElement      => 2
    case _: ScInfixTypeElement         => 3
    case _: ScExistentialTypeElement   => 4
    case _: ScWildcardTypeElement      => 5
    case _: ScFunctionalTypeElement | _: ScPolyFunctionTypeElement | _: ScTypeLambdaTypeElement | _: ScMatchTypeElement  => 6
    case _                             => throw new IllegalArgumentException(s"Unknown type element $te")
  }

  private def patternPrecedence(pattern: ScPattern): Int = pattern match {
    case _: ScCompositePattern => 13
    case _: ScTypedPattern     => 12
    case _: ScNamingPattern    => 11
    case i: ScInfixPattern     => 1 + infixOperationPriority(i)
    case _                     => 0
  }

  @tailrec
  private def getInnermostNonParen(node: ScalaPsiElement): ScalaPsiElement = node match {
    // since we go down, it can only be the same tree
    case ScParenthesizedElement(inner) => getInnermostNonParen(inner)
    case _                             => node
  }

  private def isIndivisibleRepeatedParamType(parenthesized: ScParenthesizedElement): Boolean = parenthesized match {
    case ScParenthesizedElement(inner) && ChildOf(pt: ScParameterType) =>
      pt.isRepeatedParameter && !isIndivisible(inner)
    case _ => false
  }


  private object SameKindParentAndInner {
    def unapply(p: ScParenthesizedElement): Option[(p.Kind, p.Kind)] =
      for (parent <- p.sameTreeParent; inner <- p.innerElement) yield (parent, inner)
  }

  private object NeededParenthesisAroundFunctionalType {
    /*
     * Parenthesis here are not redundant
     * test { blub: (Int => Int) => ??? }
     */
    def unapply(p: ScParenthesisedTypeElement): Boolean =
      p.innerElement.exists(_.is[ScFunctionalTypeElement]) &&
        p.getParent.is[ScParameterType] &&
        p.nextVisibleLeaf.exists(_.elementType == ScalaTokenTypes.tFUNTYPE)
  }
}
