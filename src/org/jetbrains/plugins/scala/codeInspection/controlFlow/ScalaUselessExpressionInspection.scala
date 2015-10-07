package org.jetbrains.plugins.scala
package codeInspection.controlFlow

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.codeInspection.controlFlow.ScalaUselessExpressionInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection, RemoveElementQuickFix}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScCaseClause, ScCaseClauses}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.{ScFunctionType, ScType}
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, types}
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

/**
 * Nikolay.Tropin
 * 2014-09-22
 */
class ScalaUselessExpressionInspection extends AbstractInspection("ScalaUselessExpression", "Useless expression") {
  override def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case expr: ScExpression if IntentionAvailabilityChecker.checkInspection(this, expr.getParent) =>
      if (canResultInSideEffectsOnly(expr) && exprHasNoSideEffects(expr)) {
        val message = "Useless expression"
        val removeElemFix = new RemoveElementQuickFix("Remove expression", expr)
        val addReturnKeywordFix = PsiTreeUtil.getParentOfType(expr, classOf[ScFunctionDefinition]) match {
          case null => Seq.empty
          case fun if fun.returnType.getOrAny != types.Unit => Seq(new AddReturnQuickFix(expr))
          case _ => Seq.empty
        }

        holder.registerProblem(expr, message, removeElemFix +: addReturnKeywordFix: _*)
      }
  }

  private def exprHasNoSideEffects(expr: ScExpression): Boolean = expr match {
    case lit: ScInterpolatedStringLiteral => 
      import org.jetbrains.plugins.scala.lang.psi.api.base.InterpolatedStringType._
      Seq(STANDART, FORMAT, RAW).contains(lit.getType)
    case lit: ScLiteral => true
    case ScParenthesisedExpr(inner) => exprHasNoSideEffects(inner)
    case typed: ScTypedStmt => exprHasNoSideEffects(typed.expr)
    case ref: ScReferenceExpression =>
      if (hasImplicitConversion(ref)) false
      else {
        ref.qualifier.forall(exprHasNoSideEffects) && (ref.resolve() match {
          case Both(b: ScBindingPattern, ScalaPsiUtil.inNameContext(pd: ScPatternDefinition))
            if pd.hasModifierProperty("lazy") => false
          case bp: ScBindingPattern =>
            val tp = bp.getType(TypingContext.empty)
            !ScFunctionType.isFunctionType(tp.getOrAny)
          case _: ScObject => true
          case p: ScParameter
            if !p.isCallByNameParameter &&
                    !ScFunctionType.isFunctionType(p.getRealParameterType(TypingContext.empty).getOrAny)=> true
          case _: ScSyntheticFunction => true
          case m: PsiMethod => methodHasNoSideEffects(m, ref.qualifier.flatMap(_.getType().toOption))
          case _ => false
        })
      }
    case t: ScTuple => t.exprs.forall(exprHasNoSideEffects)
    case inf: ScInfixExpr if inf.isAssignmentOperator => false
    case ScSugarCallExpr(baseExpr, operation, args) =>
      val checkOperation = operation match {
        case ref if hasImplicitConversion(ref) => false
        case ResolvesTo(_: ScSyntheticFunction) => true
        case ResolvesTo(m: PsiMethod) => methodHasNoSideEffects(m, baseExpr.getType().toOption)
        case _ => false
      }
      checkOperation && exprHasNoSideEffects(baseExpr) && args.forall(exprHasNoSideEffects)
    case ScMethodCall(baseExpr, args) =>
      val (checkQual, typeOfQual) = baseExpr match {
        case ScReferenceExpression.withQualifier(qual) => (exprHasNoSideEffects(qual), qual.getType().toOption)
        case _ => (true, None)
      }
      val checkBaseExpr = baseExpr match {
        case _ if hasImplicitConversion(baseExpr) => false
        case ResolvesTo(m: PsiMethod) => methodHasNoSideEffects(m, typeOfQual)
        case ResolvesTo(_: ScSyntheticFunction) => true
        case ResolvesTo(td: ScTypedDefinition) =>
          val withApplyText = baseExpr.getText + ".apply" + args.map(_.getText).mkString("(", ", ", ")")
          val withApply = ScalaPsiElementFactory.createExpressionWithContextFromText(withApplyText, expr.getContext, expr)
          withApply match {
            case ScMethodCall(ResolvesTo(m: PsiMethod), _) =>
              methodHasNoSideEffects(m, typeOfQual)
            case _ => false
          }
          case _ => exprHasNoSideEffects(baseExpr)
      }
      checkQual && checkBaseExpr && args.forall(exprHasNoSideEffects)
    case _ => false
  }

  def hasImplicitConversion(refExpr: ScExpression) = {
    refExpr match {
      case ref: ScReferenceExpression =>
        ref.bind().exists(rr => rr.implicitConversionClass.isDefined || rr.implicitFunction.isDefined)
      case _ => false
    }
  }

  private def methodHasNoSideEffects(m: PsiMethod, typeOfQual: Option[ScType] = None): Boolean = {
    val methodClazzName = Option(m.containingClass).map(_.qualifiedName)

    methodClazzName match {
      case Some(fqn) =>
        val name = fqn + "." + m.name
        if (ScalaCodeStyleSettings.nameFitToPatterns(name, methodsFromObjectWithSideEffects))
          return false
      case _ =>
    }

    val clazzName = typeOfQual.flatMap(ScType.extractDesignatorSingletonType).orElse(typeOfQual) match {
      case Some(tp) => ScType.extractClass(tp).map(_.qualifiedName)
      case None => methodClazzName
    }

    clazzName.map(_ + "." + m.name) match {
      case Some(name) => ScalaCodeStyleSettings.nameFitToPatterns(name, immutableClasses)
      case None => false
    }
  }

  private def isLastInBlock(expr: ScExpression): Boolean = expr match {
    case ChildOf(bl: ScBlock) => bl.lastExpr.contains(expr)
    case ChildOf(_: ScPatternDefinition | _: ScFunctionDefinition | _: ScVariableDefinition) =>
      !expr.isInstanceOf[ScBlock]
    case _ => false
  }

  private def isInBlock(expr: ScExpression): Boolean = expr match {
    case ChildOf(bl: ScBlock) => true
    case _ => false
  }

  private def canResultInSideEffectsOnly(expr: ScExpression): Boolean = {
    def isNotLastInBlock: Boolean = {
      val parents = expr.parentsInFile.takeWhile {
        case ms: ScMatchStmt if ms.expr.exists(PsiTreeUtil.isAncestor(_, expr, false)) => false
        case ifStmt: ScIfStmt if ifStmt.condition.exists(PsiTreeUtil.isAncestor(_, expr, false)) => false
        case _: ScBlock | _: ScParenthesisedExpr | _: ScIfStmt |
             _: ScCaseClause | _: ScCaseClauses | _: ScMatchStmt |
             _: ScTryStmt | _: ScCatchBlock => true
        case _ => false
      }
      (expr +: parents.toSeq).exists {
        case e: ScExpression => isInBlock(e) && !isLastInBlock(e)
        case _ => false
      }
    }
    def isInReturnPositionForUnitFunction: Boolean = {
      Option(PsiTreeUtil.getParentOfType(expr, classOf[ScFunctionDefinition])) match {
        case Some(fun) if fun.returnType.getOrAny == types.Unit => fun.returnUsages().contains(expr)
        case _ => false
      }
    }
    isNotLastInBlock || isInReturnPositionForUnitFunction
  }
}

class AddReturnQuickFix(e: ScExpression) extends AbstractFixOnPsiElement("Add return keyword", e) {
  override def doApplyFix(project: Project): Unit = {
    val expr = getElement
    val retStmt = ScalaPsiElementFactory.createExpressionWithContextFromText(s"return ${expr.getText}", expr.getContext, expr)
    expr.replaceExpression(retStmt, removeParenthesis = true)
  }
}

object ScalaUselessExpressionInspection {

  private val excludeNonString = Seq("StringBuffer._", "StringBuilder._").map("exclude:java.lang." + _)

  private val javaWrappers = Seq("Integer", "Byte", "Character", "Short", "Boolean", "Long", "Double", "Float")
          .map(name => s"java.lang.$name._")

  private val otherJavaClasses = Seq("java.lang.String._", "java.lang.Math._", "java.math.BigInteger._", "java.math.BigDecimal._")

  private val scalaValueClasses = Seq("Boolean", "Byte", "Char", "Double", "Float", "Int", "Lont", "Unit")
          .map(name => s"scala.$name._")

  private val otherFromScalaPackage = Seq("Option._", "Some._", "Tuple._", "Symbol._").map("scala." + _)

  private val fromScalaUtil = Seq("Either", "Failure", "Left", "Right", "Success", "Try")
          .map(name => s"scala.util.$name._")

  private val fromScalaMath = Seq("scala.math.BigInt._", "scala.math.BigDecimal._")

  private val immutableCollections = Seq("scala.collection.immutable._")

  val immutableClasses =
    (excludeNonString ++: javaWrappers ++: otherJavaClasses ++:
      scalaValueClasses ++: otherFromScalaPackage ++: fromScalaUtil ++: fromScalaMath ++: immutableCollections).toArray

  val methodsFromObjectWithSideEffects = Seq("wait", "finalize", "notifyAll", "notify")
          .map("java.lang.Object." + _).toArray
}