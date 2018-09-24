package org.jetbrains.plugins.scala
package codeInspection.typeChecking

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.{PsiElement, PsiMethod}
import com.siyeh.ig.psiutils.MethodUtils
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.codeInspection.typeChecking.ComparingUnrelatedTypesInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractInspection, InspectionBundle}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiClassExt, ResolvesTo}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{ScTypePresentation, _}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType

import scala.annotation.tailrec

/**
  * Nikolay.Tropin
  * 5/30/13
  */

object ComparingUnrelatedTypesInspection {
  val inspectionName = InspectionBundle.message("comparing.unrelated.types.name")
  val inspectionId = "ComparingUnrelatedTypes"

  private val seqFunctions = Seq("contains", "indexOf", "lastIndexOf")

  private def cannotBeCompared(type1: ScType, type2: ScType): Boolean = {
    val stdTypes = type1.projectContext.stdTypes
    import stdTypes._

    var types = Seq(type1, type2)
    if (types.exists(undefinedTypeAlias)) return false

    types = types.map(extractActualType)
    if (!types.contains(Null)) {
      types = types.map {
        case tp => fqnBoxedToScType.getOrElse(tp.canonicalText.stripPrefix("_root_."), tp)
      }
    }

    if (types.forall(isNumericType)) return false

    val Seq(unboxed1, unboxed2) = types
    ComparingUtil.isNeverSubType(unboxed1, unboxed2) && ComparingUtil.isNeverSubType(unboxed2, unboxed1)
  }

  private def isNumericType(`type`: ScType): Boolean = {
    val stdTypes = `type`.projectContext.stdTypes
    import stdTypes._

    `type` match {
      case Byte | Char | Short | Int | Long | Float | Double => true
      case ScDesignatorType(c: ScClass) => c.supers.headOption.exists(_.qualifiedName == "scala.math.ScalaNumber")
      case _ => false
    }
  }

  private def undefinedTypeAlias(`type`: ScType) = `type`.isAliasType.exists {
    case AliasType(_, Right(lower), Right(upper)) => !lower.equiv(upper)
    case _ => false
  }

  @tailrec
  private def extractActualType(`type`: ScType): ScType = `type`.isAliasType match {
    case Some(AliasType(_, Right(rhs), _)) => extractActualType(rhs)
    case _ => `type`.tryExtractDesignatorSingleton
  }
}

class ComparingUnrelatedTypesInspection extends AbstractInspection(inspectionName) {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case MethodRepr(expr, Some(left), Some(oper), Seq(right)) if Seq("==", "!=", "ne", "eq", "equals") contains oper.refName =>
      val needHighlighting = oper.resolve() match {
        case _: ScSyntheticFunction => true
        case m: PsiMethod if MethodUtils.isEquals(m) => true
        case _ => false
      }
      if (needHighlighting) {
        Seq(left, right).map(_.`type`().map(_.tryExtractDesignatorSingleton)) match {
          case Seq(Right(leftType), Right(rightType)) if cannotBeCompared(leftType, rightType) =>
            val message = generateComparingUnrelatedTypesMsg(leftType, rightType)
            holder.registerProblem(expr, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
          case _ =>
        }
      }
    case MethodRepr(_, Some(baseExpr), Some(ResolvesTo(fun: ScFunction)), Seq(arg, _*)) if mayNeedHighlighting(fun) =>
      for {
        ParameterizedType(_, Seq(elemType)) <- baseExpr.`type`().toOption.map(_.tryExtractDesignatorSingleton)
        argType <- arg.`type`().toOption
        if cannotBeCompared(elemType, argType)
      } {
        val message = generateComparingUnrelatedTypesMsg(elemType, argType)
        holder.registerProblem(arg, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
    case IsInstanceOfCall(call) =>
      val qualType = call.referencedExpr match {
        case ScReferenceExpression.withQualifier(q) => q.`type`().map(_.tryExtractDesignatorSingleton).toOption
        case _ => None
      }
      val argType = call.arguments.headOption.flatMap(_.`type`().toOption)
      for {
        t1 <- qualType
        t2 <- argType
        if cannotBeCompared(t1, t2)
      } {
        val message = generateComparingUnrelatedTypesMsg(t1, t2)
        holder.registerProblem(call, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
  }

  private def generateComparingUnrelatedTypesMsg(firstType: ScType, secondType: ScType): String = {
    val nonSingleton1 = firstType.extractDesignatorSingleton.getOrElse(firstType)
    val nonSingleton2 = secondType.extractDesignatorSingleton.getOrElse(secondType)
    val (firstTypeText, secondTypeText) = ScTypePresentation.different(nonSingleton1, nonSingleton2)
    InspectionBundle.message("comparing.unrelated.types.hint", firstTypeText, secondTypeText)
  }

  private def mayNeedHighlighting(fun: ScFunction): Boolean = {
    if (!seqFunctions.contains(fun.name) || fun.isLocal) return false

    val className = fun.containingClass.qualifiedName.toOption.getOrElse("")

    className.startsWith("scala.collection") && className.contains("Seq") ||
      Seq("scala.Option", "scala.Some").contains(className) && fun.name == "contains"
  }
}
