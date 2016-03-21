package org.jetbrains.plugins.scala.util

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.extensions.{Both, PsiClassExt, PsiMemberExt, PsiNamedElementExt, ResolvesTo}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.{ScFunctionType, ScType, ScTypeExt, ScalaType}

/**
  * @author Nikolay.Tropin
  */
object SideEffectsUtil {

  private val immutableClasses = listImmutableClasses

  private val methodsFromObjectWithSideEffects = Seq("wait", "finalize", "notifyAll", "notify")
    .map("java.lang.Object." + _).toArray

  def hasNoSideEffects(expr: ScExpression)
                      (implicit typeSystem: TypeSystem = expr.typeSystem): Boolean = expr match {
    case lit: ScInterpolatedStringLiteral =>
      import org.jetbrains.plugins.scala.lang.psi.api.base.InterpolatedStringType._
      Seq(STANDART, FORMAT, RAW).contains(lit.getType)
    case _: ScLiteral => true
    case _: ScThisReference => true
    case und: ScUnderscoreSection if und.bindingExpr.isEmpty => true
    case ScParenthesisedExpr(inner) => hasNoSideEffects(inner)
    case typed: ScTypedStmt => hasNoSideEffects(typed.expr)
    case ref: ScReferenceExpression =>
      if (hasImplicitConversion(ref)) false
      else {
        ref.qualifier.forall(hasNoSideEffects) && (ref.resolve() match {
          case Both(b: ScBindingPattern, ScalaPsiUtil.inNameContext(pd: ScPatternDefinition))
            if pd.hasModifierProperty("lazy") => false
          case bp: ScBindingPattern =>
            val tp = bp.getType(TypingContext.empty)
            !ScFunctionType.isFunctionType(tp.getOrAny)
          case _: ScObject => true
          case p: ScParameter
            if !p.isCallByNameParameter &&
              !ScFunctionType.isFunctionType(p.getRealParameterType(TypingContext.empty).getOrAny) => true
          case _: ScSyntheticFunction => true
          case m: PsiMethod => methodHasNoSideEffects(m, ref.qualifier.flatMap(_.getType().toOption))
          case _ => false
        })
      }
    case t: ScTuple => t.exprs.forall(hasNoSideEffects)
    case inf: ScInfixExpr if inf.isAssignmentOperator => false
    case ScSugarCallExpr(baseExpr, operation, args) =>
      val checkOperation = operation match {
        case ref if hasImplicitConversion(ref) => false
        case ref if ref.refName.endsWith("_=") => false
        case ResolvesTo(_: ScSyntheticFunction) => true
        case ResolvesTo(m: PsiMethod) => methodHasNoSideEffects(m, baseExpr.getType().toOption)
        case _ => false
      }
      checkOperation && hasNoSideEffects(baseExpr) && args.forall(hasNoSideEffects)
    case ScMethodCall(baseExpr, args) =>
      val (checkQual, typeOfQual) = baseExpr match {
        case ScReferenceExpression.withQualifier(qual) => (hasNoSideEffects(qual), qual.getType().toOption)
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
        case _ => hasNoSideEffects(baseExpr)
      }
      checkQual && checkBaseExpr && args.forall(hasNoSideEffects)
    case _ => false
  }

  private def listImmutableClasses = {
    val excludeNonString = Seq("StringBuffer._", "StringBuilder._").map("exclude:java.lang." + _)

    val javaWrappers = Seq("Integer", "Byte", "Character", "Short", "Boolean", "Long", "Double", "Float")
      .map(name => s"java.lang.$name._")

    val otherJavaClasses = Seq("java.lang.String._", "java.lang.Math._", "java.math.BigInteger._", "java.math.BigDecimal._")

    val scalaValueClasses = Seq("Boolean", "Byte", "Char", "Double", "Float", "Int", "Lont", "Unit")
      .map(name => s"scala.$name._")

    val otherFromScalaPackage = Seq("Option._", "Some._", "Tuple._", "Symbol._").map("scala." + _)

    val fromScalaUtil = Seq("Either", "Failure", "Left", "Right", "Success", "Try")
      .map(name => s"scala.util.$name._")

    val fromScalaMath = Seq("scala.math.BigInt._", "scala.math.BigDecimal._")

    val immutableCollections = Seq("scala.collection.immutable._")

    (excludeNonString ++: javaWrappers ++: otherJavaClasses ++:
      scalaValueClasses ++: otherFromScalaPackage ++: fromScalaUtil ++: fromScalaMath ++: immutableCollections).toArray
  }

  private def hasImplicitConversion(refExpr: ScExpression) = {
    refExpr match {
      case ref: ScReferenceExpression =>
        ref.bind().exists(rr => rr.implicitConversionClass.isDefined || rr.implicitFunction.isDefined)
      case _ => false
    }
  }

  private def methodHasNoSideEffects(m: PsiMethod, typeOfQual: Option[ScType] = None)
                                    (implicit typeSystem: TypeSystem): Boolean = {
    val methodClazzName = Option(m.containingClass).map(_.qualifiedName)

    methodClazzName match {
      case Some(fqn) =>
        val name = fqn + "." + m.name
        if (ScalaCodeStyleSettings.nameFitToPatterns(name, methodsFromObjectWithSideEffects))
          return false
      case _ =>
    }

    val clazzName = typeOfQual.flatMap(ScalaType.extractDesignatorSingletonType).orElse(typeOfQual) match {
      case Some(tp) => tp.extractClass().map(_.qualifiedName)
      case None => methodClazzName
    }

    clazzName.map(_ + "." + m.name) match {
      case Some(name) => ScalaCodeStyleSettings.nameFitToPatterns(name, immutableClasses)
      case None => false
    }
  }
}

