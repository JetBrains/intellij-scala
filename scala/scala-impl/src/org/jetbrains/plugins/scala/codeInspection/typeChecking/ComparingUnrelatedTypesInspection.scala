package org.jetbrains.plugins.scala.codeInspection.typeChecking

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.psi.PsiMethod
import com.siyeh.ig.psiutils.MethodUtils
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.collections.MethodRepr
import org.jetbrains.plugins.scala.codeInspection.typeChecking.ComparingUnrelatedTypesInspection._
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParameterizedTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScGiven}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypePresentation
import org.jetbrains.plugins.scala.util.CommonQualifiedNames

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq

object ComparingUnrelatedTypesInspection {
  val inspectionId = "ComparingUnrelatedTypes"

  sealed abstract class Comparability(val shouldNotBeCompared: Boolean)
  object Comparability {
    case object Comparable extends Comparability(false)
    case object Incomparable extends Comparability(true)
    case object LikelyIncomparable extends Comparability(true)
  }
  private val isIdentityFunction = Set("ne", "eq")
  private val isComparingFunctions = Set("==", "!=", "equals") | isIdentityFunction
  private val seqFunctions = ArraySeq("contains", "indexOf", "lastIndexOf")

  private val uninterestingPsiBaseClass = ArraySeq(
    CommonQualifiedNames.JavaIoSerializableFqn,
    CommonQualifiedNames.JavaLangObjectFqn,
    CommonQualifiedNames.JavaLangComparableFqn
  )

  private def isUninterestingBaseClass(typ: ScType): Boolean = {
    val text = typ.toPsiType.getCanonicalText
    uninterestingPsiBaseClass.exists(text.startsWith)
  }

  // see this check in scalac: https://github.com/scala/scala/blob/8c86b7d7136839538cca0ff8fca50f59437564c0/src/compiler/scala/tools/nsc/typechecker/RefChecks.scala#L968
  private def checkComparability(type1: ScType, type2: ScType, isBuiltinOperation: => Boolean): Comparability = {
    val stdTypes = type1.projectContext.stdTypes
    import stdTypes._

    val types = Seq(type1, type2)
    // a comparison with AnyRef is always ok, because of autoboxing
    // i.e:
    //   val anyRef: AnyRef = new Integer(4)
    //   anyRef == 4                 <- true
    //   anyRef.isInstanceOf[Int]    <- true
    if (types.contains(AnyRef)) return Comparability.Comparable
    if (types.exists(undefinedTypeAlias)) return Comparability.Comparable
    if (types.exists(_.isAny)) return Comparability.Comparable
    if (types.exists(_.isNothing)) return Comparability.Comparable


    val actualTypes = types.map(extractActualType)
    val oneTypeIsNull = actualTypes.contains(Null)

    val unboxed =
      if (oneTypeIsNull) actualTypes
      else actualTypes.map(tp => fqnBoxedToScType.getOrElse(tp.canonicalText.stripPrefix("_root_."), tp))

    if (unboxed.forall(isNumericType)) return Comparability.Comparable

    val Seq(unboxed1, unboxed2) = unboxed

    val sameClasses = unboxed1.extractClass.nonEmpty && unboxed1.extractClass == unboxed2.extractClass

    if (unboxed1.equiv(unboxed2) || sameClasses) {
      return Comparability.Comparable
    }

    if (isBuiltinOperation && ComparingUtil.isNeverSubType(unboxed1, unboxed2) && ComparingUtil.isNeverSubType(unboxed2, unboxed1))
      return Comparability.Incomparable

    if (oneTypeIsNull) {
      return Comparability.Comparable
    }


    // check if their lub is interesting
    val lub = unboxed1 lub unboxed2
    val noInterestingBaseClass = lub match {
      case ScCompoundType(components, _, _) => components.forall(isUninterestingBaseClass)
      case ty => isUninterestingBaseClass(ty)
    }

    if (noInterestingBaseClass) Comparability.LikelyIncomparable
    else Comparability.Comparable
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
    case AliasType(_, _, Right(rhs)) => extractActualType(rhs)
    case _                           => `type`.widen
  }

  private def hasNonDefaultEquals(ty: ScType): Boolean = {
    ty.extractClassSimple().exists { ty =>
      ty.findMethodsByName("equals", true).exists { m =>
        !m.containingClass.isJavaLangObject && !m.isInstanceOf[ScSyntheticFunction] && MethodUtils.isEquals(m)
      }
    }
  }

  private def hasCanEqual(expr: ScExpression, source: ScType, target: ScType): Boolean = {
    lazy val expressionTypes: Seq[ScType] = List(source, target)
    lazy val canEqualExists: Boolean = expr
      .contexts
      .flatMap(_.children)
      .filterByType[ScGiven]
      .filter(_.`type`().map(_.canonicalText.matches("_root_\\.scala\\.CanEqual\\[.+?, .+?]")).getOrElse(false))
      .flatMap(_.children.filterByType[ScParameterizedTypeElement])
      .map(_.typeArgList.typeArgs.flatMap(_.`type`().map(_.tryExtractDesignatorSingleton).toSeq))
      .exists(_
        .zip(expressionTypes)
        .forall {
          case (givenType, compType) =>
            !checkComparability(givenType, compType, isBuiltinOperation = true).shouldNotBeCompared
        }
      )

    val wideSource: ScType = source.widenIfLiteral
    // Even though CanEqual[Primitive | String, _] can be defined and will satisfy compiler in strictEquals mode,
    // it is not possible to override equals method on the primitives or Strings
    !wideSource.isPrimitive &&
      !wideSource.canonicalText.matches("_root_\\.java\\.lang\\.String") &&
      (expr.isCompilerStrictEqualityMode || canEqualExists)
  }
}

class ComparingUnrelatedTypesInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case MethodRepr(expr, Some(left), Some(oper), Seq(right)) if isComparingFunctions(oper.refName) =>
      // "blub" == 3
      val needHighlighting = oper.resolve() match {
        case _: ScSyntheticFunction => true
        case m: PsiMethod if MethodUtils.isEquals(m) => true
        case _ => false
      }
      if (needHighlighting) {
        Seq(left, right).map(_.`type`().map(_.tryExtractDesignatorSingleton)) match {
          case Seq(Right(leftType), Right(rightType)) =>
            val isBuiltinOperation = isIdentityFunction(oper.refName) || !hasNonDefaultEquals(leftType)
            val comparability = checkComparability(leftType, rightType, isBuiltinOperation)
            if ((!expr.isInScala3File && comparability.shouldNotBeCompared) ||
              (expr.isInScala3File && comparability.shouldNotBeCompared && !hasCanEqual(expr, leftType, rightType))) {
              val message = generateComparingUnrelatedTypesMsg(leftType, rightType)(expr)
              holder.registerProblem(expr, message)
            }
          case _ =>
        }
      }
    case MethodRepr(_, Some(baseExpr), Some(ref @ ResolvesTo(fun: ScFunction)), Seq(arg, _*)) if mayNeedHighlighting(fun) =>
      // Seq("blub").contains(3)
      for {
        ParameterizedType(_, Seq(elemType)) <- receiverType(baseExpr, ref).map(_.tryExtractDesignatorSingleton)
        argType <- arg.`type`().toOption
        comparability = checkComparability(elemType, argType, isBuiltinOperation = !hasNonDefaultEquals(elemType))
        if (!baseExpr.isInScala3File && comparability.shouldNotBeCompared) ||
          (baseExpr.isInScala3File && comparability.shouldNotBeCompared && !hasCanEqual(baseExpr, elemType, argType))
      } {
        val message = generateComparingUnrelatedTypesMsg(elemType, argType)(arg)
        holder.registerProblem(arg, message)
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
        comparability = checkComparability(t1, t2, isBuiltinOperation = true)
        if comparability == Comparability.Incomparable
      } {
        val message = generateComparingUnrelatedTypesMsg(t1, t2)(call)
        holder.registerProblem(call, message)
      }
    case _ =>
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

  private def receiverType(expr: ScExpression, invoked: ScReferenceExpression): Option[ScType] =
    invoked.bind().flatMap(_.implicitType).
      orElse(expr.`type`().toOption)
}
