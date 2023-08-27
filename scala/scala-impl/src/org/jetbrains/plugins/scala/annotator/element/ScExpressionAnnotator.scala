package org.jetbrains.plugins.scala.annotator.element

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.{shouldIgnoreTypeMismatchIn, smartCheckConformance}
import org.jetbrains.plugins.scala.annotator.{ScalaAnnotationHolder, TypeMismatchError}
import org.jetbrains.plugins.scala.annotator.quickfix.{AddBreakoutQuickFix, ChangeTypeFix, WrapInOptionQuickFix}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.ExpressionTypeResult
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.DesignatorOwner
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScMethodType
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.annotation.tailrec

object ScExpressionAnnotator extends ElementAnnotator[ScExpression] {
  override def annotate(element: ScExpression, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit =
    annotateImpl(element, typeAware)

  private[annotator] def annotateImpl(element: ScExpression, typeAware: Boolean, fromBlock: Boolean = false)
                                     (implicit holder: ScalaAnnotationHolder): Unit = {
    // TODO Annotating ScUnderscoreSection is technically correct, but reveals previously hidden red code in ScalacTestdataHighlightingTest.tuples_1.scala
    // TODO see visitUnderscoreExpression in ScalaAnnotator
    //  EDIT: the only failing test was scala/scala-impl/testdata/scalacTests/pos/t3864/tuples_1.scala
    //  it was extracted to https://youtrack.jetbrains.com/issue/SCL-18683
    if (element.is[ScUnderscoreSection] && element.getParent.is[ScParameter])
      return

    if (!fromBlock && annotatedByBlockExpr(element))
      return

    val compiled = element.getContainingFile.asOptionOf[ScalaFile].exists(_.isCompiled)

    if (!compiled) {
      def charAt(offset: Int): Option[Char] =
        Option(PsiDocumentManager.getInstance(element.getProject).getDocument(holder.getCurrentAnnotationSession.getFile))
          .filter(_.getTextLength > offset).map(_.getImmutableCharSequence.charAt(offset))

      element match {
        // Highlight type ascription differently from type mismatch (handled in ScTypedExpressionAnnotator), SCL-15544
        case Parent(_: ScTypedExpression) | Parent((_: ScParenthesisedExpr) & Parent(_: ScTypedExpression)) => ()

        // Highlight if-then with non-Unit expected type as incomplete rather than type mismatch, SCL-19447
        case it: ScIf if it.thenExpression.nonEmpty && it.elseExpression.isEmpty &&
          typeAware && it.expectedType().exists(et => it.`type`().exists(!_.conforms(et))) =>
          val offset = it.getTextOffset + it.getTextLength

          val builder =
            holder.newAnnotation(HighlightSeverity.ERROR, ScalaBundle.message("else.expected"))
              .range(TextRange.create(offset, offset))

          if (charAt(offset).contains('\n')) {
            builder.afterEndOfLine
          }
          builder.create()

        // Highlight empty case clauses with non-Unit expected type as incomplete rather than type mismatch, SCL-19447
        case block: ScBlock if block.getParent.is[ScCaseClause] && block.exprs.isEmpty &&
          typeAware && block.expectedType().exists(et => block.`type`().exists(!_.conforms(et))) =>

          val builder =
            holder.newAnnotation(HighlightSeverity.ERROR, ScalaBundle.message("expression.expected"))
              .range(block)

          if (charAt(block.getTextOffset + block.getTextLength).contains('\n')) {
            builder.afterEndOfLine
          }
          builder.create()
        case _ => checkExpressionType(element, typeAware)
      }
    }
  }

  // TODO Can `type` do this automatically?
  def adjusted(tpe: ScType, expected: Option[ScType]): ScType = if (expected.exists(_.is[ScLiteralType])) tpe else tpe.widenIfLiteral

  def isAggregate(e: ScExpression): Boolean = e match {
    case ScIf(_, thenExpr, elseExpr) =>
      thenExpr.exists(_.`type`().exists(thenType => elseExpr.exists(_.`type`().exists(elseType => adjusted(thenType, e.expectedType()).equiv(adjusted(elseType, e.expectedType()))))))
    case ScMatch(_, Seq(firstCase, otherCases @ _*)) if otherCases.nonEmpty && firstCase.resultExpr.isDefined && otherCases.forall(_.resultExpr.isDefined)=>
      firstCase.expr.exists(_.`type`().exists(t0 => otherCases.forall(_.expr.exists(_.`type`().exists(tn => adjusted(t0, e.expectedType()).equiv(adjusted(tn, e.expectedType())))))))
    case _ => false
  }

  def isAggregatePart(e: ScExpression): Boolean = e match {
    case Parent(it @ ScIf(_, thenExpr, elseExpr)) if thenExpr.contains(e) || elseExpr.contains(e) => isAggregate(it)
    case Parent(Parent(ScCaseClause(_, _, block) & Parent(Parent(m: ScMatch)))) if block.exists(_.getFirstChild == e) => isAggregate(m)
    case _ => false
  }

  def checkExpressionType(
    element:             ScExpression,
    typeAware:           Boolean,
    fromFunctionLiteral: Boolean = false,
    inDesugaring:        Boolean = false
  )(implicit
    holder: ScalaAnnotationHolder
  ): Unit = {
    implicit val project: Project = element.getProject

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

    val hintsEnabled = ScalaProjectSettings.in(element.getProject).isTypeMismatchHints

    // TODO rename (it's not about size, but about inner / outer expressions)
    def isTooBigToHighlight(expr: ScExpression): Boolean = expr match {
      case m: ScMatch                                => !(hintsEnabled && isAggregate(m))
      case bl: ScBlock if bl.resultExpression.isDefined => !fromFunctionLiteral
      case i: ScIf if i.elseExpression.isDefined     => !(hintsEnabled && isAggregate(i))
      case _: ScFunctionExpr                         => !fromFunctionLiteral && (expr.getTextRange.getLength > 20 || expr.textContains('\n'))
      case _: ScTry                                  => true
      case e                                         => hintsEnabled && isAggregatePart(e)
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
      val smartExpectedType = element.smartExpectedType(fromUnderscore)
      val ExpressionTypeResult(exprType, _, implicitFunction) =
        element.getTypeAfterImplicitConversion(expectedOption = smartExpectedType, fromUnderscore = fromUnderscore)

      if (isTooBigToHighlight(element) || (!fromFunctionLiteral && isInArgumentPosition(element)) || shouldNotHighlight(element)) return

      val typeEx = element.expectedTypeEx(fromUnderscore)
      typeEx match {
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
              var fixes: Seq[(IntentionAction, TextRange)] = Seq.empty
              def addFix(fix: IntentionAction): Unit = {
                fixes :+= (fix, element.getTextRange)
              }

              if (WrapInOptionQuickFix.isAvailable(expectedType, exprType)) {
                addFix(new WrapInOptionQuickFix(element, expectedType, exprType))
              }
              if (AddBreakoutQuickFix.isAvailable(element)) {
                addFix(new AddBreakoutQuickFix(element))
              }
              typeElement match {
                case Some(te) if te.getContainingFile == element.getContainingFile =>
                  val changeTypeFix = new ChangeTypeFix(te, exprType.getOrNothing)
                  addFix(changeTypeFix)
                  addFix(changeTypeFix)
                case _ =>
              }
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

              // Don't show type mismatch when the expression is of a singleton type that has an apply method, while a non-singleton type is expected, SCL-17669
              tp match {
                case t: DesignatorOwner if t.isSingleton => () // Expected type is a singleton type
                case _ => exprType match {
                  case Right(t: DesignatorOwner) if t.isSingleton =>
                    t.element.asOptionOf[ScTypeDefinition].flatMap(_.methodsByName("apply").nextOption()).map(_.method) match {
                      case Some(method: ScFunctionDefinition) =>
                        val missingParameters = method.parameters.map(p => p.getName + ": " + p.`type`().getOrNothing.presentableText(target)).mkString(", ")
                        holder.createErrorAnnotation(target, ScalaBundle.message("annotator.error.unspecified.value.parameters", missingParameters))
                        return
                      case None => () // The type has no apply method
                      case _ => ???
                    }
                  case _ => () // The expression is not of a singleton type
                }
              }

              TypeMismatchError.register(target, tp, exprType.getOrNothing,
                blockLevel = 2, canBeHint = !element.is[ScTypedExpression] && !inDesugaring, fixes) { (expected, actual) =>
                ScalaBundle.message("expr.type.does.not.conform.expected.type", actual, expected)
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
