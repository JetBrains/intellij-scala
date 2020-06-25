package org.jetbrains.plugins.scala
package annotator
package element

import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.{annotationWithoutHighlighting, shouldIgnoreTypeMismatchIn, smartCheckConformance}
import org.jetbrains.plugins.scala.annotator.quickfix.{AddBreakoutQuickFix, ChangeTypeFix, WrapInOptionQuickFix}
import org.jetbrains.plugins.scala.annotator.usageTracker.UsageTracker.registerUsedImports
import org.jetbrains.plugins.scala.extensions.{&&, _}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.ExpressionTypeResult
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScMethodType
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec

object ScExpressionAnnotator extends ElementAnnotator[ScExpression] {

  override def annotate(element: ScExpression, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    // TODO Annotating ScUnderscoreSection is technically correct, but reveals previously hidden red code in ScalacTestdataHighlightingTest.tuples_1.scala
    // TODO see visitUnderscoreExpression in ScalaAnnotator
    if (element.isInstanceOf[ScUnderscoreSection]) {
      return
    }

    val compiled = element.getContainingFile.asOptionOf[ScalaFile].exists(_.isCompiled)

    if (!compiled) {
      // Highlight type ascription differently from type mismatch (handled in ScTypedExpressionAnnotator), SCL-15544
      element match {
        case Parent(_: ScTypedExpression) =>
        case Parent((_: ScParenthesisedExpr) && Parent(_: ScTypedExpression)) =>
        case _ => checkExpressionType(element, typeAware)
      }
    }
  }

  def checkExpressionType(element: ScExpression, typeAware: Boolean, fromFunctionLiteral: Boolean = false)
                         (implicit holder: ScalaAnnotationHolder): Unit = {
    implicit val ctx: ProjectContext = element

    @tailrec
    def isInArgumentPosition(expr: ScExpression): Boolean =
      expr.getContext match {
        case _: ScArgumentExprList               => true
        case ScInfixExpr.withAssoc(_, _, `expr`) => true
        case b: ScBlockExpr                      => isInArgumentPosition(b)
        case p: ScParenthesisedExpr              => isInArgumentPosition(p)
        case t: ScTuple                          => isInArgumentPosition(t)
        case ScIf(Some(`expr`), _, _)            => false
        case i: ScIf                             => isInArgumentPosition(i)
        case m: ScMatch                          => isInArgumentPosition(m)
        case _                                   => false
      }

    // TODO rename (it's not about size, but about inner / outer expressions)
    def isTooBigToHighlight(expr: ScExpression): Boolean = expr match {
      case _: ScMatch                                => true
      case bl: ScBlock if bl.resultExpression.isDefined => !fromFunctionLiteral
      case i: ScIf if i.elseExpression.isDefined     => true
      case _: ScFunctionExpr                         => !fromFunctionLiteral && (expr.getTextRange.getLength > 20 || expr.textContains('\n'))
      case _: ScTry                                  => true
      case _                                         => false
    }

    def shouldNotHighlight(expr: ScExpression): Boolean = expr.getContext match {
      case a: ScAssignment if a.rightExpression.contains(expr) && a.isDynamicNamedAssignment => true
      case t: ScTypedExpression if t.isSequenceArg                                           => true
      case param: ScParameter if !param.isDefaultParam                                       => true //performance optimization
      case param: ScParameter =>
        param.getRealParameterType match {
          case Right(paramType) if paramType.extractClass.isDefined =>
            false //do not check generic types. See SCL-3508
          case _ => true
        }
      case ass: ScAssignment if ass.isNamedParameter =>
        true //that's checked in application annotator
      case _ => false
    }

    def checkExpressionTypeInner(fromUnderscore: Boolean): Unit = {
      val ExpressionTypeResult(exprType, importUsed, implicitFunction) =
        element.getTypeAfterImplicitConversion(expectedOption = element.smartExpectedType(fromUnderscore), fromUnderscore = fromUnderscore)

      registerUsedImports(element, importUsed)

      if (isTooBigToHighlight(element) || (!fromFunctionLiteral && isInArgumentPosition(element)) || shouldNotHighlight(element)) return

      element.expectedTypeEx(fromUnderscore) match {
        case Some((tp: ScType, _)) if tp equiv api.Unit => //do nothing
        case Some((tp: ScType, typeElement)) =>
          val expectedType = Right(tp)
          implicitFunction match {
            case Some(_) =>
            //todo:
            /*val typeFrom = expr.getType(TypingContext.empty).getOrElse(Any)
            val typeTo = exprType.getOrElse(Any)
            val exprText = expr.getText
            val range = expr.getTextRange
            showImplicitUsageAnnotation(exprText, typeFrom, typeTo, fun, range, holder,
              EffectType.LINE_UNDERSCORE, Color.LIGHT_GRAY)*/
            case None => //do nothing
          }
          val conformance = smartCheckConformance(expectedType, exprType)
          if (!conformance) {
            if (typeAware) {
              element.getParent match {
                case assign: ScAssignment if exprType.exists(ScalaPsiUtil.isUnderscoreEq(assign, _)) => return
                case _ =>
              }
              if (ScMethodType.hasMethodType(element)) {
                return
              }
              if (shouldIgnoreTypeMismatchIn(element, fromFunctionLiteral)) {
                return
              }
              val annotation = {
                val target = element match {
                  // TODO fine-grained ranges
                  // When present, highlight type ascription, not expression, SCL-15544
                  case typedExpression: ScTypedExpression =>
                    (typedExpression.typeElement, typedExpression.expr.getTypeAfterImplicitConversion().tr) match {
                      // Don't show additional type mismatch when there's an error in type ascription (handled in ScTypedExpressionAnnotator), SCL-15544
                      case (Some(typeElement), Right(actualType)) if !actualType.conforms(typeElement.calcType) => return
                      case _ =>
                    }
                    typedExpression.typeElement.getOrElse(element)
                  case _ => element
                }
                TypeMismatchError.register(target, tp, exprType.getOrNothing, blockLevel = 2, canBeHint = !element.is[ScTypedExpression]) { (expected, actual) =>
                  ScalaBundle.message("expr.type.does.not.conform.expected.type", actual, expected)
                }
              }
              if (WrapInOptionQuickFix.isAvailable(element, expectedType, exprType)) {
                val wrapInOptionFix = new WrapInOptionQuickFix(element, expectedType, exprType)
                annotation.registerFix(wrapInOptionFix)
              }
              if (AddBreakoutQuickFix.isAvailable(element)) {
                annotation.registerFix(new AddBreakoutQuickFix(element))
              }
              typeElement match {
                case Some(te) if te.getContainingFile == element.getContainingFile =>
                  val fix = new ChangeTypeFix(te, exprType.getOrNothing)
                  annotation.registerFix(fix)
                  val teAnnotation = annotationWithoutHighlighting(te)
                  teAnnotation.registerFix(fix)
                case _ =>
              }
            }
          }
        case _ => //do nothing
      }

    }
    if (ScUnderScoreSectionUtil.isUnderscoreFunction(element)) {
      checkExpressionTypeInner(fromUnderscore = true)
    }
    checkExpressionTypeInner(fromUnderscore = false)
  }
}
