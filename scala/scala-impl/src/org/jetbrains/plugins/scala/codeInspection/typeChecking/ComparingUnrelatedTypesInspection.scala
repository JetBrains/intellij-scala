package org.jetbrains.plugins.scala
package codeInspection.typeChecking

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.{PsiElement, PsiMethod}
import com.siyeh.ig.psiutils.MethodUtils
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.codeInspection.typeChecking.ComparingUnrelatedTypesInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiClassExt, ResolvesTo}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType

import scala.annotation.{nowarn, tailrec}
import scala.collection.compat.immutable.ArraySeq

/**
  * Nikolay.Tropin
  * 5/30/13
  */

object ComparingUnrelatedTypesInspection {
  val inspectionName: String = ScalaInspectionBundle.message("comparing.unrelated.types.name")
  val inspectionId = "ComparingUnrelatedTypes"

  sealed abstract class Comparability(val shouldNotBeCompared: Boolean)
  object Comparability {
    case object Comparable extends Comparability(false)
    case object Incomparable extends Comparability(true)
    case object LikelyIncomparable extends Comparability(true)
  }
  private val comparingFunctions = ArraySeq("==", "!=", "ne", "eq", "equals")
  private val seqFunctions = ArraySeq("contains", "indexOf", "lastIndexOf")

  private def checkComparability(type1: ScType, type2: ScType): Comparability = {
    val stdTypes = type1.projectContext.stdTypes
    import stdTypes._

    var types = Seq(type1, type2)
    if (types.exists(undefinedTypeAlias)) return Comparability.Comparable

    // a comparison with AnyRef is always ok, because of autoboxing
    // i.e:
    //   val anyRef: AnyRef = new Integer(4)
    //   anyRef == 4                 <- true
    //   anyRef.isInstanceOf[Int]    <- true
    if (types.contains(AnyRef)) return Comparability.Comparable

    types = types.map(extractActualType)
    if (!types.contains(Null)) {
      types = types.map(tp => fqnBoxedToScType.getOrElse(tp.canonicalText.stripPrefix("_root_."), tp))
    }

    if (types.forall(isNumericType)) return Comparability.Comparable

    val Seq(unboxed1, unboxed2) = types
    if (ComparingUtil.isNeverSubType(unboxed1, unboxed2) && ComparingUtil.isNeverSubType(unboxed2, unboxed1))
      return Comparability.Incomparable

    if (!unboxed1.weakConforms(unboxed2) && !unboxed2.weakConforms(unboxed1))
      return Comparability.LikelyIncomparable

    Comparability.Comparable
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

  private def undefinedTypeAlias(`type`: ScType) = `type` match {
    case AliasType(_, Right(lower), Right(upper)) => !lower.equiv(upper)
    case _                                        => false
  }

  @tailrec
  private def extractActualType(`type`: ScType): ScType = `type` match {
    case AliasType(_, Right(rhs), _) => extractActualType(rhs)
    case _                           => `type`.widen
  }
}

@nowarn("msg=" + AbstractInspection.DeprecationText)
class ComparingUnrelatedTypesInspection extends AbstractInspection(inspectionName) {

  override def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Any] = {
    case MethodRepr(expr, Some(left), Some(oper), Seq(right)) if comparingFunctions contains oper.refName =>
      // "blub" == 3
      val needHighlighting = oper.resolve() match {
        case _: ScSyntheticFunction => true
        case m: PsiMethod if MethodUtils.isEquals(m) => true
        case _ => false
      }
      if (needHighlighting) {
        Seq(left, right).map(_.`type`().map(_.tryExtractDesignatorSingleton)) match {
          case Seq(Right(leftType), Right(rightType)) =>
            val comparability = checkComparability(leftType, rightType)
            if (comparability.shouldNotBeCompared) {
              val message = generateComparingUnrelatedTypesMsg(leftType, rightType)(expr)
              holder.registerProblem(expr, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
            }
          case _ =>
        }
      }
    case MethodRepr(_, Some(baseExpr), Some(ResolvesTo(fun: ScFunction)), Seq(arg, _*)) if mayNeedHighlighting(fun) =>
      // Seq("blub").contains(3)
      for {
        ParameterizedType(_, Seq(elemType)) <- baseExpr.`type`().toOption.map(_.tryExtractDesignatorSingleton)
        argType <- arg.`type`().toOption
        comparability = checkComparability(elemType, argType)
        if comparability.shouldNotBeCompared
      } {
        val message = generateComparingUnrelatedTypesMsg(elemType, argType)(arg)
        holder.registerProblem(arg, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
    case IsInstanceOfCall(call) =>
      // "blub".isInstanceOf[Integer]
      val qualType = call.referencedExpr match {
        case ScReferenceExpression.withQualifier(q) => q.`type`().map(_.tryExtractDesignatorSingleton).toOption
        case _ => None
      }
      val argType = call.arguments.headOption.flatMap(_.`type`().toOption)
      for {
        t1 <- qualType
        t2 <- argType
        comparability = checkComparability(t1, t2)
        if comparability == Comparability.Incomparable
      } {
        val message = generateComparingUnrelatedTypesMsg(t1, t2)(call)
        holder.registerProblem(call, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
  }

  @Nls
  private def generateComparingUnrelatedTypesMsg(firstType: ScType, secondType: ScType)
                                                (implicit tpc: TypePresentationContext): String = {
    val nonSingleton1 = firstType.widen
    val nonSingleton2 = secondType.widen
    val (firstTypeText, secondTypeText) = TypePresentation.different(nonSingleton1, nonSingleton2)
    ScalaInspectionBundle.message("comparing.unrelated.types.hint", firstTypeText, secondTypeText)
  }

  private def mayNeedHighlighting(fun: ScFunction): Boolean = {
    if (!seqFunctions.contains(fun.name) || fun.isLocal) return false

    val className = fun.containingClass.qualifiedName.toOption.getOrElse("")

    className.startsWith("scala.collection") && className.contains("Seq") ||
      Seq("scala.Option", "scala.Some").contains(className) && fun.name == "contains"
  }
}
