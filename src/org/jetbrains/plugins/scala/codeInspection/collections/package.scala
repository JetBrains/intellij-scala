package org.jetbrains.plugins.scala.codeInspection

import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.{CachedValueProvider, CachedValuesManager, PsiTreeUtil}
import com.intellij.psi.{PsiElement, PsiMethod, PsiType}
import org.jetbrains.plugins.scala.codeInspection.InspectionsUtil.isExpressionOfType
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaEvaluatorBuilderUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings.nameFitToPatterns
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClauses
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.{InferUtil, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.{ExtractClass, TypeSystem}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_9
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

import scala.annotation.tailrec

/**
 * @author Nikolay.Tropin
 */
package object collections {
  def likeCollectionClasses = ScalaApplicationSettings.getInstance().getLikeCollectionClasses
  def likeOptionClasses = ScalaApplicationSettings.getInstance().getLikeOptionClasses

  val monadicMethods = Set("map", "flatMap", "filter", "withFilter")
  val foldMethodNames = Set("foldLeft", "/:", "foldRight", ":\\", "fold")
  val reduceMethodNames = Set("reduce", "reduceLeft", "reduceRight")

  def invocation(methodName: String) = new InvocationTemplate(methodName == _)
  def invocation(methodNames: Set[String]) = new InvocationTemplate(methodNames.contains)

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

  private[collections] val `.isDefined` = invocation(Set("isDefined", "nonEmpty")).from(likeOptionClasses)
  private[collections] val `.isEmptyOnOption` = invocation("isEmpty").from(likeOptionClasses)
  private[collections] val `.isEmpty` = invocation("isEmpty").from(likeCollectionClasses)
  private[collections] val `.nonEmpty` = invocation("nonEmpty").from(likeCollectionClasses)

  private[collections] val `.fold` = invocation(foldMethodNames).from(likeCollectionClasses)
  private[collections] val `.foldLeft` = invocation(Set("foldLeft", "/:")).from(likeCollectionClasses)
  private[collections] val `.reduce` = invocation(reduceMethodNames).from(likeCollectionClasses)
  private[collections] val `.getOrElse` = invocation("getOrElse").from(likeOptionClasses)
  private[collections] val `.getOnMap` = invocation("get").from(likeCollectionClasses).ref(checkResolveToMap)
  private[collections] val `.mapOnOption` = invocation("map").from(likeOptionClasses).ref(checkScalaVersion)
  private[collections] val `.sort` = invocation(Set("sortWith", "sortBy", "sorted")).from(likeCollectionClasses)
  private[collections] val `.reverse` = invocation("reverse").from(likeCollectionClasses)
  private[collections] val `.iterator` = invocation("iterator").from(likeCollectionClasses)
  private[collections] val `.apply` = invocation("apply")
  private[collections] val `.zip` = invocation("zip").from(likeCollectionClasses)
  private[collections] val `.indices` = invocation("indices").from(likeCollectionClasses)
  private[collections] val `.take` = invocation("take").from(likeCollectionClasses)
  private[collections] val `.drop` = invocation("drop").from(likeCollectionClasses)
  private[collections] val `.sameElements` = invocation("sameElements").from(likeCollectionClasses)
  private[collections] val `.corresponds` = invocation("corresponds").from(likeCollectionClasses)

  private[collections] val `.to` = invocation("to").from(Array("RichInt", "RichChar", "RichLong", "RichDouble", "RichFloat").map("scala.runtime." + _))

  private[collections] val `!=` = invocation("!=")
  private[collections] val `==` = invocation(Set("==", "equals"))
  private[collections] val `>` = invocation(">")
  private[collections] val `>=` = invocation(">=")
  private[collections] val `!` = invocation(Set("!", "unary_!"))
  private[collections] val `-` = invocation("-")
  private[collections] val `+` = invocation("+")

  private[collections] val `.toCollection` = new InvocationTemplate(name => name.startsWith("to") && name != "toString").from(likeCollectionClasses)
  private[collections] val `.toSet` = invocation("toSet").from(likeCollectionClasses)
  private[collections] val `.toIterator` = invocation("toIterator").from(likeCollectionClasses)

  private[collections] val `.lift` = invocation("lift").from(Array("scala.PartialFunction"))

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

  object IfStmt {
    def unapply(expr: ScExpression): Option[(ScExpression, ScExpression, ScExpression)] = {
      expr match {
        case ScIfStmt(Some(c), Some(stripped(tb)), Some(stripped(eb))) => Some(c, tb, eb)
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

  class FunctionExpressionWithReturnTypeTemplate(tp: ScType) {
    def unapply(expr: ScExpression)(implicit typeSystem: TypeSystem = expr.typeSystem): Boolean = {
      expr.getType(TypingContext.empty) match {
        case Success(result, _) =>
          result match {
            case ScFunctionType(returnType, _) => returnType.conforms(tp)
            case _ => false
          }
        case _ => false
      }
    }
  }

  val returnsBoolean = new FunctionExpressionWithReturnTypeTemplate(Boolean)

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
        case typed: ScTypedStmt if typed.expr.isInstanceOf[ScUnderscoreSection] => true
        case und: ScUnderscoreSection => true
        case _ => false
      }
    }
  }

  def invocationText(qual: ScExpression, methName: String, args: ScExpression*): String = {
    val qualText = qual.getText
    val argsText = argListText(args)
    qual match {
      case _ childOf ScInfixExpr(`qual`, _, _) if args.size == 1 =>
        s"${qual.getText} $methName ${args.head.getText}"
      case infix: ScInfixExpr => s"($qualText).$methName$argsText"
      case _ => s"$qualText.$methName$argsText"
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
      case Seq(b @ ScBlock(fe: ScFunctionExpr)) => b.getText
      case Seq(ScBlock(stmt: ScBlockStatement)) => s"(${stmt.getText})"
      case Seq(b: ScBlock) => b.getText
      case Seq((fe: ScFunctionExpr) childOf (b: ScBlockExpr)) => b.getText
      case Seq(other) => s"(${other.getText})"
      case seq if seq.size > 1 => seq.map(_.getText).mkString("(", ", ", ")")
      case _ => ""
    }
  }


  private def checkResolveToMap(memberRef: ScReferenceElement): Boolean = memberRef.resolve() match {
    case m: ScMember => Option(m.containingClass).exists(_.name.toLowerCase.contains("map"))
    case _ => false
  }

  private def checkScalaVersion(elem: PsiElement): Boolean = { //there is no Option.fold in Scala 2.9
    elem.scalaLanguageLevel.map(_ > Scala_2_9).getOrElse(true)
  }

  def implicitParameterExistsFor(methodName: String, baseExpr: ScExpression): Boolean = {
    val expression = ScalaPsiElementFactory.createExpressionWithContextFromText(s"${baseExpr.getText}.$methodName", baseExpr.getContext, baseExpr)
    implicitParameterExistsFor(expression)
  }

  def implicitParameterExistsFor(expr: ScExpression): Boolean = {
    expr.findImplicitParameters match {
      case Some(Seq(srr: ScalaResolveResult)) if srr.element.name == InferUtil.notFoundParameterName => false
      case Some(Seq(srr: ScalaResolveResult, _*)) => true
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
    val name = parameter.getName
    val visitor = new ScalaRecursiveElementVisitor() {
      override def visitReferenceExpression(ref: ScReferenceExpression) {
        if (ref.refName == name && ref.resolve() == parameter) result = false
        super.visitReferenceExpression(ref)
      }
    }
    expr.accept(visitor)
    result
  }

  def checkResolve(expr: ScExpression, patterns: Array[String]): Boolean = {
    expr match {
      case ref: ScReferenceExpression =>
        ref.resolve() match {
          case obj: ScObject =>
            nameFitToPatterns(obj.qualifiedName, patterns)
          case member: ScMember =>
            val clazz = member.containingClass
            if (clazz == null || clazz.qualifiedName == null) false
            else nameFitToPatterns(clazz.qualifiedName, patterns)
          case _ => false
        }
      case _ => false
    }
  }

  def isOfClassFrom(expr: ScExpression, patterns: Array[String])
                   (implicit typeSystem: TypeSystem = expr.typeSystem): Boolean = {
    if (expr == null) return false
    expr.getType() match {
      case Success(tp, _) =>
        ScalaType.extractDesignatorSingletonType(tp).getOrElse(tp) match {
          case ExtractClass(cl) if nameFitToPatterns(cl.qualifiedName, patterns) => true
          case _ => false
        }
      case _ => false
    }
  }

  def isOption(expr: ScExpression): Boolean = isOfClassFrom(expr, likeOptionClasses)

  def isArray(expr: ScExpression): Boolean = expr match {
    case ExpressionType(JavaArrayType(_)) => true
    case _ => isOfClassFrom(expr, Array("scala.Array"))
  }

  def isSet(expr: ScExpression): Boolean = isExpressionOfType("scala.collection.GenSet", expr)

  def isSeq(expr: ScExpression): Boolean = isExpressionOfType("scala.collection.GenSeq", expr)

  def isIndexedSeq(expr: ScExpression): Boolean = isExpressionOfType("scala.collection.IndexedSeq", expr)

  def isMap(expr: ScExpression): Boolean = isExpressionOfType("scala.collection.GenMap", expr)

  def isSortedSet(expr: ScExpression) = isExpressionOfType("scala.collection.SortedSet", expr)

  def isSortedMap(expr: ScExpression) = isExpressionOfType("scala.collection.SortedMap", expr)

  def isIterator(expr: ScExpression) = isExpressionOfType("scala.collection.Iterator", expr)

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
                checkResolve(ref, Array("scala.collection.mutable._", "scala.collection.Iterator"))
      }

      def isSetter(ref: ScReferenceExpression): Boolean = {
        ref.refName.startsWith("set") || ref.refName.endsWith("_=")
      }

      def hasUnitReturnType(ref: ScReferenceExpression)
                           (implicit typeSystem: TypeSystem = ref.typeSystem): Boolean = {
        ref match {
          case MethodRepr(ExpressionType(ScFunctionType(_, _)), _, _, _) => false
          case ResolvesTo(fun: ScFunction) => fun.hasUnitResultType
          case ResolvesTo(m: PsiMethod) => m.getReturnType == PsiType.VOID
          case _ => false
        }
      }

      object definedOutside {
        def unapply(ref: ScReferenceElement): Option[PsiElement] = ref match {
          case ResolvesTo(elem: PsiElement) if !PsiTreeUtil.isAncestor(expr, elem, false) => Some(elem)
          case _ => None
        }
      }

      val predicate: (PsiElement) => Boolean = {
        case `expr` => true
        case (ScFunctionExpr(_, _) | (_: ScCaseClauses)) childOf `expr` => true
        case (e: ScExpression) childOf `expr` if ScUnderScoreSectionUtil.underscores(e).nonEmpty => true
        case fun: ScFunctionDefinition => false
        case elem: PsiElement => !ScalaEvaluatorBuilderUtil.isGenerateClass(elem)
      }

      val sameLevelIterator = expr.depthFirst(predicate).filter(predicate)

      sameLevelIterator.collect {
        case assign @ ScAssignStmt(definedOutside(ScalaPsiUtil.inNameContext(_: ScVariable)), _) =>
          assign
        case assign @ ScAssignStmt(mc @ ScMethodCall(definedOutside(_), _), _) if mc.isUpdateCall =>
          assign
        case infix @ ScInfixExpr(definedOutside(ScalaPsiUtil.inNameContext(v: ScVariable)), _, _) if infix.isAssignmentOperator =>
          infix
        case MethodRepr(itself, Some(definedOutside(ScalaPsiUtil.inNameContext(v @ (_ : ScVariable | _: ScValue)))), Some(ref), _)
          if isSideEffectCollectionMethod(ref) || isSetter(ref) || hasUnitReturnType(ref) => itself
        case MethodRepr(itself, None, Some(ref @ definedOutside(_)), _) if hasUnitReturnType(ref) => itself
      }.toSeq
    }
  }

  def exprsWithSideEffects(expr: ScExpression) = CachedValuesManager.getCachedValue(expr, new SideEffectsProvider(expr))

  def hasSideEffects(expr: ScExpression) = exprsWithSideEffects(expr).nonEmpty

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
    case MethodRepr(itself: ScMethodCall, Some(base), None, _) => refNameId(base)
    case MethodRepr(_, _,Some(ref), _) => Some(ref.nameId)
    case _ => None
  }

  implicit class PsiElementRange(val elem: PsiElement) extends AnyVal {
    def start: Int = elem.getTextRange.getStartOffset
    def end: Int = elem.getTextRange.getEndOffset
  }
}

