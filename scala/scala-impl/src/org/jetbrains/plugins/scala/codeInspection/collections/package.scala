package org.jetbrains.plugins.scala.codeInspection

import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.{CachedValueProvider, CachedValuesManager, PsiTreeUtil}
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaEvaluatorBuilderUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClauses
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, JavaArrayType, PartialFunctionType}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq

/**
 * @author Nikolay.Tropin
 */
package object collections {
  def likeCollectionClasses: ArraySeq[String] = ArraySeq.unsafeWrapArray(ScalaApplicationSettings.getInstance().getLikeCollectionClasses)
  def likeOptionClasses: ArraySeq[String] = ArraySeq.unsafeWrapArray(ScalaApplicationSettings.getInstance().getLikeOptionClasses)

  val monadicMethods = Set("map", "flatMap", "filter", "withFilter")
  val foldMethodNames = Set("foldLeft", "/:", "foldRight", ":\\", "fold")
  val reduceMethodNames = Set("reduce", "reduceLeft", "reduceRight")

  def invocation(methodName: String) = new Qualified(methodName == _)
  def invocation(methodNames: Set[String]) = new Qualified(methodNames.contains)

  def unqualifed(methodName: String) = new Unqualified(methodName == _)
  def unqualifed(methodName: Set[String]) = new Unqualified(methodName.contains)

  private[collections] val `.exists` = invocation("exists").from(likeCollectionClasses)
  private[collections] val `.forall` = invocation("forall").from(likeCollectionClasses)
  private[collections] val `.filter` = invocation("filter").from(likeCollectionClasses)
  private[collections] val `.filterNot` = invocation("filterNot").from(likeCollectionClasses)
  private[collections] val `.map` = invocation("map").from(likeCollectionClasses)
  private[collections] val `.headOption` = invocation("headOption").from(likeCollectionClasses)
  private[collections] val `.lastOption` = invocation("lastOption").from(likeCollectionClasses)
  private[collections] val `.head` = invocation("head").from(likeCollectionClasses)
  private[collections] val `.last` = invocation("last").from(likeCollectionClasses)
  private[collections] val `.sizeOrLength` = invocation(Set("size", "length")).from(likeCollectionClasses)
  private[collections] val `.find` = invocation("find").from(likeCollectionClasses)
  private[collections] val `.contains` = invocation("contains").from(likeCollectionClasses)
  private[collections] val `.flatten` = invocation("flatten").from(likeCollectionClasses)
  private[collections] val `.flatMap` = invocation("flatMap").from(likeCollectionClasses)
  private[collections] val `.collect` = invocation("collect").from(likeCollectionClasses)

  private[collections] val `.isDefined` = invocation(Set("isDefined", "nonEmpty")).from(likeOptionClasses)
  private[collections] val `.isEmptyOnOption` = invocation("isEmpty").from(likeOptionClasses)
  private[collections] val `.isEmpty` = invocation("isEmpty").from(likeCollectionClasses)
  private[collections] val `.nonEmpty` = invocation("nonEmpty").from(likeCollectionClasses)

  private[collections] val `.fold` = invocation(foldMethodNames).from(likeCollectionClasses)
  private[collections] val `.foldLeft` = invocation(Set("foldLeft", "/:")).from(likeCollectionClasses)
  private[collections] val `.reduce` = invocation(reduceMethodNames).from(likeCollectionClasses)
  private[collections] val `.getOrElse` = invocation("getOrElse").from(likeOptionClasses)
  private[collections] val `.get` = invocation("get").from(likeOptionClasses)
  private[collections] val `.getOnMap` = invocation("get").from(likeCollectionClasses).ref(checkResolveToMap)
  private[collections] val `.mapOnOption` = invocation("map").from(likeOptionClasses)
  private[collections] val `.sort` = invocation(Set("sortWith", "sortBy", "sorted")).from(likeCollectionClasses)
  private[collections] val `.sorted` = invocation("sorted").from(likeCollectionClasses)
  private[collections] val `.sortBy` = invocation("sortBy").from(likeCollectionClasses)
  private[collections] val `.reverse` = invocation("reverse").from(likeCollectionClasses)
  private[collections] val `.iterator` = invocation("iterator").from(likeCollectionClasses)
  private[collections] val `.apply` = invocation("apply")
  private[collections] val `.zip` = invocation("zip").from(likeCollectionClasses)
  private[collections] val `.unzip` = invocation("unzip").from(likeCollectionClasses)
  private[collections] val `.unzip3` = invocation("unzip3").from(likeCollectionClasses)
  private[collections] val `.indices` = invocation("indices").from(likeCollectionClasses)
  private[collections] val `.take` = invocation("take").from(likeCollectionClasses)
  private[collections] val `.drop` = invocation("drop").from(likeCollectionClasses)
  private[collections] val `.sameElements` = invocation("sameElements").from(likeCollectionClasses)
  private[collections] val `.corresponds` = invocation("corresponds").from(likeCollectionClasses)

  private[collections] val `.toString` = invocation("toString") // on everything
  private[collections] val `.to` = invocation("to").from(ArraySeq("RichInt", "RichChar", "RichLong", "RichDouble", "RichFloat").map("scala.runtime." + _))

  val `!=`: Qualified = invocation("!=")
  val `==`: Qualified = invocation(Set("==", "equals"))
  val `>`: Qualified = invocation(">")
  val `>=`: Qualified = invocation(">=")
  val `<`: Qualified = invocation("<")
  val `<=`: Qualified = invocation("<=")
  val `!`: Qualified = invocation(Set("!", "unary_!"))
  val `-`: Qualified = invocation("-")
  val `+`: Qualified = invocation("+")

  private val isSpecialStringToConversions = Set(
    "toString", "toLowerCase", "toUpperCase"
  )
  private[collections] val `.toCollection` = new Qualified(name => name.startsWith("to") && !isSpecialStringToConversions(name)).from(likeCollectionClasses)
  private[collections] val `.toSet` = invocation("toSet").from(likeCollectionClasses)
  private[collections] val `.toIterator` = invocation("toIterator").from(likeCollectionClasses)

  private[collections] val `.lift` = invocation("lift").from(ArraySeq(PartialFunctionType.TypeName))

  private[collections] val `.monadicMethod` = invocation(monadicMethods).from(likeCollectionClasses)

  object scalaNone {
    def unapply(expr: ScExpression): Boolean = {
      expr match {
        case ResolvesTo(obj: ScObject) if obj.qualifiedName == "scala.None" => true
        case _ => false
      }
    }
  }

  object scalaSome {
    def unapply(expr: ScExpression): Option[ScExpression] = expr match {
      case MethodRepr(_, _, Some(ref), Seq(e)) if ref.refName == "Some" =>
        ref.resolve() match {
          case m: ScMember if m.containingClass.qualifiedName == "scala.Some" => Some(e)
          case _ => None
        }
      case _ => None
    }
  }

  object scalaOption {
    def unapply(expr: ScExpression): Option[ScExpression] = expr match {
      case MethodRepr(_, _, Some(ref), Seq(e)) if ref.refName == "Option" =>
        ref.resolve() match {
          case m: ScMember if m.containingClass.qualifiedName == "scala.Option" => Some(e)
          case _ => None
        }
      case _ => None
    }
  }

  object IfStmt {
    def unapply(expr: ScExpression): Option[(ScExpression, ScExpression, ScExpression)] = {
      expr match {
        case ScIf(Some(c), Some(stripped(tb)), Some(stripped(eb))) => Some(c, tb, eb)
        case _ => None
      }
    }
  }

  object literal {
    def unapply(expr: ScExpression): Option[String] = {
      expr match {
        case lit: ScLiteral => Some(lit.getText)
        case _ => None
      }
    }
  }

  object returnsBoolean {
    def unapply(expr: ScExpression): Boolean = {
      import expr.projectContext

      expr.`type`() match {
        case Right(result) =>
          result match {
            case FunctionType(returnType, _) => returnType.conforms(api.Boolean)
            case _ => false
          }
        case _ => false
      }
    }
  }

  object binaryOperation {
    def unapply(expr: ScExpression): Option[String] = {
      val operRef = stripped(expr) match {
        case ScFunctionExpr(Seq(x, y), Some(result)) =>
          def checkResolve(left: ScExpression, right: ScExpression) = (stripped(left), stripped(right)) match {
            case (leftRef: ScReferenceExpression, rightRef: ScReferenceExpression) =>
              Set(leftRef.resolve(), rightRef.resolve()) equals Set(x, y)
            case _ => false
          }
          stripped(result) match {
            case ScInfixExpr(left, oper, right) if checkResolve(left, right) => Some(oper)
            case ScMethodCall(refExpr: ScReferenceExpression, Seq(left, right)) if checkResolve(left, right) => Some(refExpr)
            case _ => None
          }
        case ScInfixExpr(underscore(), oper, underscore()) => Some(oper)
        case ScMethodCall(refExpr: ScReferenceExpression, Seq(underscore(), underscore()))  => Some(refExpr)
        case _ => None
      }
      operRef.map(_.refName)
    }
  }

  class BinaryOperationOnParameterAndExprTemplate(operName: String) {
    def unapply(expr: ScExpression): Option[ScExpression] = {
      stripped(expr) match {
        case ScFunctionExpr(Seq(x), Some(result)) =>
          stripped(result) match {
            case ScInfixExpr(left, oper, right) if oper.refName == operName =>
              (stripped(left), stripped(right)) match {
                case (leftRef: ScReferenceExpression, rightExpr)
                  if leftRef.resolve() == x && isIndependentOf(rightExpr, x) =>
                  Some(rightExpr)
                case (leftExpr: ScExpression, rightRef: ScReferenceExpression)
                  if rightRef.resolve() == x && isIndependentOf(leftExpr, x) =>
                  Some(leftExpr)
                case _ => None
              }
            case _ => None
          }
        case ScInfixExpr(underscore(), oper, right) if oper.refName == operName => Some(right)
        case ScInfixExpr(left, oper, underscore()) if oper.refName == operName => Some(left)
        case _ => None
      }
    }
  }

  private[collections] val `x == ` = new BinaryOperationOnParameterAndExprTemplate("==")
  private[collections] val `x != ` = new BinaryOperationOnParameterAndExprTemplate("!=")

  object andCondition {
    def unapply(expr: ScExpression): Option[ScExpression] = {
      stripped(expr) match {
        case ScFunctionExpr(Seq(x, y), Some(result)) =>
          stripped(result) match {
            case ScInfixExpr(left, oper, right) if oper.refName == "&&" =>
              (stripped(left), stripped(right)) match {
                case (leftRef: ScReferenceExpression, right: ScExpression)
                  if leftRef.resolve() == x && isIndependentOf(right, x) =>
                  val secondArgName = y.getName
                  val funExprText = secondArgName + " => " + right.getText
                  Some(ScalaPsiElementFactory.createExpressionWithContextFromText(funExprText, expr.getContext, expr))
                case _ => None
              }
            case _ => None
          }
        case ScInfixExpr(underscore(), oper, right) if oper.refName == "&&" => Some(right)
        case _ => None
      }
    }
  }

  class ParameterlessCallOnParameterTemplate(name: String) {
    def unapply(expr: ScExpression): Boolean = {
      stripped(expr) match {
        case ScFunctionExpr(Seq(x), Some(result)) =>
          stripped(result) match {
            case MethodRepr(_, Some(ResolvesTo(`x`)), Some(ref), Seq()) if ref.refName == name => true
            case _ => false
          }
        case MethodRepr(_, Some(underscore()), Some(ref), Seq()) if ref.refName == name => true
        case _ => false
      }
    }
  }

  private[collections] val `_._1` = new ParameterlessCallOnParameterTemplate("_1")
  private[collections] val `_._2` = new ParameterlessCallOnParameterTemplate("_2")

  object underscore {
    def unapply(expr: ScExpression): Boolean = {
      stripped(expr) match {
        case ScParenthesisedExpr(underscore()) => true
        case typed: ScTypedExpression if typed.expr.isInstanceOf[ScUnderscoreSection] => true
        case und: ScUnderscoreSection if und.bindingExpr.isEmpty => true
        case _ => false
      }
    }
  }

  def invocationText(qual: ScExpression, methName: String, args: ScExpression*): String = {
    def argsText = argListText(args)

    if (qual == null) {
      s"$methName$argsText"
    } else {
      val qualText = qual.getText
      qual match {
        case _ childOf ScInfixExpr(`qual`, _, _) if args.size == 1 =>
          s"$qualText $methName ${args.head.getText}"
        case _: ScInfixExpr => s"($qualText).$methName$argsText"
        case _: ScFor => s"($qualText).$methName$argsText"
        case _ => s"$qualText.$methName$argsText"
      }

    }
  }

  def invocationText(negation: Boolean, qual: ScExpression, methName: String, args: ScExpression*): String = {
    val baseText = invocationText(qual, methName, args: _*)
    qual match {
      case _ if !negation => baseText
      case _ childOf ScInfixExpr(`qual`, _, _) => s"!($baseText)"
      case _ => s"!$baseText"
    }
  }

  def argListText(args: Seq[ScExpression]): String = {
    args match {
      case Seq(p: ScParenthesisedExpr) => p.getText
      case Seq(b @ ScBlock(_: ScFunctionExpr)) => b.getText
      case Seq(ScBlock(stmt: ScBlockStatement)) => s"(${stmt.getText})"
      case Seq(b: ScBlock) => b.getText
      case Seq((_: ScFunctionExpr) childOf (b: ScBlockExpr)) => b.getText
      case Seq(other) => s"(${other.getText})"
      case seq if seq.size > 1 => seq.map(_.getText).mkString("(", ", ", ")")
      case _ => ""
    }
  }


  private def checkResolveToMap(memberRef: ScReference): Boolean = memberRef.resolve() match {
    case m: ScMember => Option(m.containingClass).exists(_.name.toLowerCase.contains("map"))
    case _ => false
  }

  def implicitParameterExistsFor(methodName: String, baseExpr: ScExpression): Boolean = {
    val expression = ScalaPsiElementFactory.createExpressionWithContextFromText(s"${baseExpr.getText}.$methodName", baseExpr.getContext, baseExpr)
    implicitParameterExistsFor(expression)
  }

  def implicitParameterExistsFor(expr: ScExpression): Boolean = {
    if (expr == null) false
    else expr.findImplicitArguments match {
      case Some(Seq(srr)) if srr.isImplicitParameterProblem => false
      case Some(Seq(_, _*)) => true
      case _ => false
    }
  }

  @tailrec
  def stripped(expr: ScExpression): ScExpression = {
    expr match {
      case ScParenthesisedExpr(inner) => stripped(inner)
      case ScBlock(inner: ScExpression) => stripped(inner)
      case _ => expr
    }
  }

  object stripped {
    def unapply(expr: ScExpression): Option[ScExpression] = Some(stripped(expr))
  }

  def isIndependentOf(expr: ScExpression, parameter: ScParameter): Boolean = {
    var result = true
    val name = parameter.name
    val visitor = new ScalaRecursiveElementVisitor() {
      override def visitReferenceExpression(ref: ScReferenceExpression): Unit = {
        if (ref.refName == name && ref.resolve() == parameter) result = false
        super.visitReferenceExpression(ref)
      }
    }
    expr.accept(visitor)
    result
  }

  def checkResolve(expr: ScExpression, patterns: Seq[String]): Boolean = {
    Option(expr).collect {
      case ref: ScReferenceExpression => ref.resolve()
    }.flatMap {
      case obj: ScObject => Some(obj)
      case member: PsiMember => Option(member.containingClass)
      case _ => None
    }.exists {
      qualifiedNameFitToPatterns(_, patterns)
    }
  }

  def isOfClassFrom(expr: ScExpression, patterns: Seq[String]): Boolean =
    expr.`type`().toOption.exists(isOfClassFrom(_, patterns))

  def isOfClassFrom(`type`: ScType, patterns: Seq[String]): Boolean =
    `type`.tryExtractDesignatorSingleton.extractClass.exists(qualifiedNameFitToPatterns(_, patterns))

  private def qualifiedNameFitToPatterns(clazz: PsiClass, patterns: Seq[String]) =
    Option(clazz).flatMap(c => Option(c.qualifiedName))
      .exists(ScalaNamesUtil.nameFitToPatterns(_, patterns, strict = false))

  def isOption(`type`: ScType): Boolean = isOfClassFrom(`type`, likeOptionClasses)

  def isOption(expr: ScExpression): Boolean = isOfClassFrom(expr, likeOptionClasses)

  def isArray(expr: ScExpression): Boolean = expr match {
    case Typeable(JavaArrayType(_)) => true
    case _ => isOfClassFrom(expr, ArraySeq("scala.Array"))
  }

  def isString: Typeable => Boolean =
    isExpressionOfType("java.lang.String")

  def isSet: Typeable => Boolean =
    isExpressionOfType("scala.collection.GenSetLike", "scala.collection.SetOps")

  def isSeq: Typeable => Boolean =
    isExpressionOfType("scala.collection.GenSeqLike", "scala.collection.SeqOps")

  def isIndexedSeq: Typeable => Boolean =
    isExpressionOfType("scala.collection.IndexedSeqLike", "scala.collection.IndexedSeqOps")

  def isNonIndexedSeq: Typeable => Boolean = expr => isSeq(expr) && !isIndexedSeq(expr)

  def isMap: Typeable => Boolean =
    isExpressionOfType("scala.collection.GenMapLike", "scala.collection.MapOps")

  def isSortedSet: Typeable => Boolean =
    isExpressionOfType("scala.collection.SortedSetLike", "scala.collection.SortedSetOps")

  def isSortedMap: Typeable => Boolean =
    isExpressionOfType("scala.collection.SortedMapLike", "scala.collection.SortedMapOps")

  def isIterator: Typeable => Boolean =
    isExpressionOfType("scala.collection.Iterator")

  private def isExpressionOfType(fqns: String*): Typeable => Boolean = {
    case Typeable(scType) => fqns.exists(conformsToTypeFromClass(scType, _)(scType.projectContext))
    case _ => false
  }

  def withoutConversions(expr: ScExpression): Typeable = new Typeable {
    override def `type`(): TypeResult = expr.getTypeWithoutImplicits()
  }

  private val sideEffectsCollectionMethods = Set("append", "appendAll", "clear", "insert", "insertAll",
    "prepend", "prependAll", "reduceToSize", "remove", "retain",
    "transform", "trimEnd", "trimStart", "update",
    "push", "pushAll", "pop", "dequeue", "dequeueAll", "dequeueFirst", "enqueue",
    "next")

  private class SideEffectsProvider(expr: ScExpression) extends CachedValueProvider[Seq[ScExpression]] {
    override def compute(): Result[Seq[ScExpression]] = Result.create(computeExprsWithSideEffects(expr), expr)

    private def computeExprsWithSideEffects(expr: ScExpression): Seq[ScExpression] = {

      def isSideEffectCollectionMethod(ref: ScReferenceExpression): Boolean = {
        val refName = ref.refName
        (refName.endsWith("=") || refName.endsWith("=:") || sideEffectsCollectionMethods.contains(refName)) &&
                checkResolve(ref, ArraySeq("scala.collection.mutable._", "scala.collection.Iterator"))
      }

      def isSetter(ref: ScReferenceExpression): Boolean = {
        ref.refName.startsWith("set") || ref.refName.endsWith("_=")
      }

      def hasUnitReturnType(ref: ScReferenceExpression): Boolean = {
        ref match {
          case MethodRepr(Typeable(FunctionType(_, _)), _, _, _) => false
          case ResolvesTo(fun: ScFunction) => fun.hasUnitResultType
          case ResolvesTo(m: PsiMethod) => m.getReturnType == PsiType.VOID
          case _ => false
        }
      }

      object definedOutside {
        def unapply(ref: ScReference): Option[PsiElement] = ref match {
          case ResolvesTo(elem: PsiElement) if !PsiTreeUtil.isAncestor(expr, elem, false) => Some(elem)
          case _ => None
        }
      }

      val predicate: (PsiElement) => Boolean = {
        case `expr` => true
        case (ScFunctionExpr(_, _) | (_: ScCaseClauses)) childOf `expr` => true
        case (e: ScExpression) childOf `expr` if ScUnderScoreSectionUtil.underscores(e).nonEmpty => true
        case _: ScFunctionDefinition => false
        case elem: PsiElement => !ScalaEvaluatorBuilderUtil.isGenerateClass(elem)
      }

      val sameLevelIterator = expr.depthFirst(predicate).filter(predicate)

      sameLevelIterator.collect {
        case assign @ ScAssignment(definedOutside(ScalaPsiUtil.inNameContext(_: ScVariable)), _) =>
          assign
        case assign @ ScAssignment(mc @ ScMethodCall(definedOutside(_), _), _) if mc.isUpdateCall =>
          assign
        case infix @ ScInfixExpr(definedOutside(ScalaPsiUtil.inNameContext(_: ScVariable)), _, _) if infix.isAssignmentOperator =>
          infix
        case MethodRepr(itself, Some(definedOutside(ScalaPsiUtil.inNameContext((_ : ScVariable | _: ScValue)))), Some(ref), _)
          if isSideEffectCollectionMethod(ref) || isSetter(ref) || hasUnitReturnType(ref) => itself
        case MethodRepr(itself, None, Some(ref @ definedOutside(_)), _) if hasUnitReturnType(ref) => itself
      }.toSeq
    }
  }

  def exprsWithSideEffects(expr: ScExpression): Seq[ScExpression] = CachedValuesManager.getCachedValue(expr, new SideEffectsProvider(expr))

  def hasSideEffects(expr: ScExpression): Boolean = exprsWithSideEffects(expr).nonEmpty

  def rightRangeInParent(expr: ScExpression, parent: ScExpression): TextRange = {
    if (expr == parent) return TextRange.create(0, expr.getTextLength)

    val endOffset = parent.getTextRange.getEndOffset

    val startOffset = expr match {
      case _ childOf ScInfixExpr(`expr`, op, _) => op.nameId.getTextOffset
      case _ childOf (ref @ ScReferenceExpression.withQualifier(`expr`)) => ref.nameId.getTextOffset
      case _ => expr.getTextRange.getEndOffset
    }
    TextRange.create(startOffset, endOffset).shiftRight( - parent.getTextOffset)
  }

  @tailrec
  def refNameId(expr: ScExpression): Option[PsiElement] = stripped(expr) match {
    case MethodRepr(_: ScMethodCall, Some(base), None, _) => refNameId(base)
    case MethodRepr(_, _,Some(ref), _) => Some(ref.nameId)
    case _ => None
  }

  implicit class PsiElementRange(private val elem: PsiElement) extends AnyVal {
    def start: Int = elem.getTextRange.getStartOffset
    def end: Int = elem.getTextRange.getEndOffset
  }

  private val `print` = unqualifed(Set("print", "println")).from(ArraySeq("scala.Predef", "java.io.PrintStream"))
  private val `.print` = invocation(Set("print", "println")).from(ArraySeq("scala.Predef", "java.io.PrintStream"))
  private val `.formatted` = invocation("formatted").from(ArraySeq("scala.Predef.StringFormat", "java.lang.String"))
  private val `.format` = invocation("format").from(ArraySeq("java.lang.String", "scala.collection.StringOps"))
  private val `.appendOnStringBuilder` = invocation("append").from(ArraySeq("java.lang.StringBuilder", "scala.collection.mutable.StringBuilder"))

  private[collections] def getToStringToMkStringSimplification(expr: ScExpression, isThing: ScExpression => Boolean, mkString: String, replace: ScExpression => SimplificationBuilder): Option[Simplification] = {
    expr match {
      // TODO infix notation?
      case `.toString`(thing) if isThing(thing) =>
        // thing.toString
        Some(replace(expr).withText(invocationText(thing, mkString)).highlightFrom(thing))
      case someString `+` thing if isString(someString) && isThing(thing) =>
        // "string" + thing
        Some(replace(thing).withText(invocationText(thing, mkString)).highlightFrom(thing))
      case thing `+` someString if isString(someString) && isThing(thing) =>
        // thing + "string"
        Some(replace(thing).withText(invocationText(thing, mkString)).highlightFrom(thing))
      case _ if isThing(expr) =>
        def result: SimplificationBuilder = replace(expr).withText(invocationText(expr, mkString)).highlightFrom(expr)

        expr.getParent match {
          case _: ScInterpolatedStringLiteral =>
            // s"start $thing end"
            Some(result.wrapInBlock())
          case null => None
          case parent =>
            parent.getParent match {
              case `.print`(_, args@_*) if args.contains(expr) =>
                // System.out.println(thing)
                Some(result)
              case `print`(args@_*) if args.contains(expr) =>
                // println(thing)
                Some(result)
              case `.format`(_, args@_*) if args.contains(expr) =>
                // String.format("%s", thing)
                // "%s".format(thing)
                Some(result)
              case `.formatted`(_, args@_*) if args.contains(expr) =>
                // "%s".formatted(thing)
                Some(result)
              case `.appendOnStringBuilder`(_, args@_*) if args.contains(expr) =>
                // new java.lang.StringBuilder.append(thing)
                // new scala.collection.mutable.StringBuilder.append(thing)
                Some(result)
              case _: ScInterpolatedStringLiteral =>
                // s"start ${thing} end"
                Some(result)
              case _ => None
            }
        }
      case _ =>
        None
    }
  }
}

