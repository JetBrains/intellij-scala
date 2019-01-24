package org.jetbrains.plugins.scala.lang.psi.annotator

import com.intellij.lang.annotation.{Annotation, AnnotationHolder}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.{annotationWithoutHighlighting, smartCheckConformance}
import org.jetbrains.plugins.scala.annotator.quickfix.{AddBreakoutQuickFix, ChangeTypeFix, WrapInOptionQuickFix}
import org.jetbrains.plugins.scala.annotator.usageTracker.UsageTracker.registerUsedImports
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.ExpressionTypeResult
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.{Annotatable, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}

import scala.annotation.tailrec

trait ScExpressionAnnotator extends Annotatable { self: ScExpression =>

  abstract override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {
    super.annotate(holder, typeAware)

    val compiled = getContainingFile.asOptionOf[ScalaFile].exists(_.isCompiled)

    if (!compiled) {
      checkExpressionType(holder, typeAware)
    }
  }

  private def checkExpressionType(holder: AnnotationHolder, typeAware: Boolean): Unit = {

    @tailrec
    def isInArgumentPosition(expr: ScExpression): Boolean =
      expr.getContext match {
        case _: ScArgumentExprList               => true
        case ScInfixExpr.withAssoc(_, _, `expr`) => true
        case b: ScBlockExpr                      => isInArgumentPosition(b)
        case p: ScParenthesisedExpr              => isInArgumentPosition(p)
        case t: ScTuple                          => isInArgumentPosition(t)
        case i: ScIf                         => isInArgumentPosition(i)
        case m: ScMatch                      => isInArgumentPosition(m)
        case _                                   => false
      }

    def isTooBigToHighlight(expr: ScExpression): Boolean = expr match {
      case _: ScMatch                            => true
      case bl: ScBlock if bl.lastStatement.isDefined => true
      case i: ScIf if i.elseExpression.isDefined     => true
      case _: ScFunctionExpr                         => true
      case _: ScTry                              => true
      case _                                         => false
    }

    def shouldNotHighlight(expr: ScExpression): Boolean = expr.getContext match {
      case a: ScAssignment if a.rightExpression.contains(expr) && a.isDynamicNamedAssignment => true
      case t: ScTypedExpression if t.isSequenceArg                                                => true
      case param: ScParameter if !param.isDefaultParam                                      => true //performance optimization
      case param: ScParameter                                                               =>
        param.getRealParameterType match {
          case Right(paramType) if paramType.extractClass.isDefined => false //do not check generic types. See SCL-3508
          case _                                                    => true
        }
      case ass: ScAssignment if ass.isNamedParameter                                        => true //that's checked in application annotator
      case _                                                                                => false
    }

    def checkExpressionTypeInner(fromUnderscore: Boolean) {
      val ExpressionTypeResult(exprType, importUsed, implicitFunction) =
        self.getTypeAfterImplicitConversion(expectedOption = self.smartExpectedType(fromUnderscore), fromUnderscore = fromUnderscore)

      registerUsedImports(this, importUsed)

      if (isTooBigToHighlight(this) || isInArgumentPosition(this) || shouldNotHighlight(this)) return

      self.expectedTypeEx(fromUnderscore) match {
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
              getParent match {
                case assign: ScAssignment if exprType.exists(ScalaPsiUtil.isUnderscoreEq(assign, _)) => return
                case _ =>
              }
              val markedPsi = (this, getParent) match {
                case (b: ScBlockExpr, _) => b.getRBrace.map(_.getPsi).getOrElse(this)
                case (_, b: ScBlockExpr) => b.getRBrace.map(_.getPsi).getOrElse(this)
                case _ => this
              }

              val (exprTypeText, expectedTypeText) = ScTypePresentation.different(exprType.getOrNothing, tp)
              val error = ScalaBundle.message("expr.type.does.not.conform.expected.type", exprTypeText, expectedTypeText)
              val annotation: Annotation = holder.createErrorAnnotation(markedPsi, error)
              if (WrapInOptionQuickFix.isAvailable(this, expectedType, exprType)) {
                val wrapInOptionFix = new WrapInOptionQuickFix(this, expectedType, exprType)
                annotation.registerFix(wrapInOptionFix)
              }
              if (AddBreakoutQuickFix.isAvailable(this)) {
                annotation.registerFix(new AddBreakoutQuickFix(this))
              }
              typeElement match {
                case Some(te) if te.getContainingFile == getContainingFile =>
                  val fix = new ChangeTypeFix(te, exprType.getOrNothing)
                  annotation.registerFix(fix)
                  val teAnnotation = annotationWithoutHighlighting(holder, te)
                  teAnnotation.registerFix(fix)
                case _ =>
              }
            }
          }
        case _ => //do nothing
      }

    }
    if (ScUnderScoreSectionUtil.isUnderscoreFunction(this)) {
      checkExpressionTypeInner(fromUnderscore = true)
    }
    checkExpressionTypeInner(fromUnderscore = false)
  }
}
