package org.jetbrains.plugins.scala
package annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.registerTypeMismatchError
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScLiteralTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScMethodLike, ScalaConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScArgumentExprList
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScConstructorOwner, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.UndefinedType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectContext

trait ConstructorAnnotator extends ApplicationAnnotator {
  // TODO duplication with application annotator.
  def annotateConstructor(constructor: ScConstructor, holder: AnnotationHolder) {
    constructor.typeElement match {
      case lit: ScLiteralTypeElement =>
        holder.createErrorAnnotation(constructor.typeElement, s"Class type required but ($lit) found")
      case _ =>
    }
    //in case if constructor is function
    constructor.reference match {
      case None => return
      case _ =>
    }

    val resolved = for {
      reference <- constructor.reference.toList
      resolveResult <- reference.resolveAllConstructors
      element = resolveResult.element

      // resolveAllConstructors might return inaccessible constructors
      // and as last resort even the class itself (in order to return at least something)
      // But note: a trait will be returned when a trait is instantiated as anonymous class
      // (of course traits cannot have constructors)
      elementShouldHaveBeenConcreteConstructor = element.isInstanceOf[ScConstructorOwner]
      if resolveResult.isAccessible && !elementShouldHaveBeenConcreteConstructor
    } yield resolveResult

    if (resolved.exists(isConstructorMalformed)) {
      holder.createErrorAnnotation(constructor.typeElement, "Constructor has malformed definition")
    }

    resolved match {
      case Seq() => holder.createErrorAnnotation(argsElementsTextRange(constructor), s"No constructor accessible from here")
      case Seq(r@ScConstructorResolveResult(constr)) if constr.effectiveParameterClauses.length > 1 && !isConstructorMalformed(r) =>
        // if there is only one well-formed, resolved, scala constructor with multiple parameter clauses,
        // check all of these clauses
        implicit val ctx: ProjectContext = constr

        val params = constr.getClassTypeParameters.map(_.typeParameters).getOrElse(Seq.empty)
        val typeArgs = constructor.typeArgList.map(_.typeArgs).getOrElse(Seq.empty)
        val substitutor = ScSubstitutor.bind(params, typeArgs)(_.calcType)
          .followed(ScSubstitutor.bind(params)(UndefinedType(_)))
          .followed(r.substitutor)

        val res = Compatibility.checkConstructorConformance(
          constructor,
          substitutor,
          constructor.arguments,
          constr.effectiveParameterClauses
        )

        annotateProblems(res.problems, r, constructor, holder)
      case _ =>
        for (r <- resolved)
          annotateProblems(r.problems, r, constructor, holder)
    }
  }

  private def annotateProblems(problems: Seq[ApplicabilityProblem], r: ScalaResolveResult, constructor: ScConstructor, holder: AnnotationHolder): Unit = {
    val element = r.element
    def argsElements = argsElementsTextRange(constructor)
    def signature = signatureOf(element)

    // mark problematic clauses where parameters are missing
    element match {
      case ScalaConstructor(constrDef) =>
        val missedParams = problems.collect { case MissedValueParameter(p) => p}
        missedParams.groupBy(parameterToArgClause(_, constrDef, constructor.arguments)).foreach {
          case (param, missing) =>
            val problematicClause = param.map(_.getTextRange).getOrElse(argsElements)

            holder.createErrorAnnotation(problematicClause,
              "Unspecified value parameters: " + missing.map(p => p.name + ": " + p.paramType.presentableText).mkString(", "))
        }
      case _ =>
    }

    // check if the found element can even be used as a constructor
    (element, constructor.arguments) match {
      case (tr: ScTrait, head +: tail) if head.exprs.nonEmpty || tail.nonEmpty =>
        // new Trait() {} is allowed!
        // but not   new Trait()() {}
        // or        new Trait(i: Int) {}
        holder.createErrorAnnotation(tail.foldLeft(head.getTextRange)(_ union _.getTextRange),
          s"${tr.name} is a trait and thus has no constructor")
      case _ =>
    }

    problems.foreach {
      case ExcessArgument(argument) =>
        holder.createErrorAnnotation(argument, s"Too many arguments for constructor$signature")
      case TypeMismatch(expression, expectedType) =>
        expression.`type`().foreach {
          registerTypeMismatchError(_, expectedType, holder, expression)
        }
      case MissedParametersClause(_) =>
        // try to mark the right-most bracket
        val markRange = constructor.arguments.lastOption
          .map(_.getTextRange.getEndOffset)
          .map(off => TextRange.create(off - 1, off))
          .getOrElse(constructor.getTextRange)
        holder.createErrorAnnotation(markRange, s"Missing argument list for constructor$signature")
      case MissedValueParameter(_) => // simultaneously handled above
      case UnresolvedParameter(_) => // don't show function inapplicability, unresolved
      case MalformedDefinition() => // handled before to avoid duplications
      case ExpansionForNonRepeatedParameter(expression) =>
        holder.createErrorAnnotation(expression, "Expansion for non-repeated parameter")
      case PositionalAfterNamedArgument(argument) =>
        holder.createErrorAnnotation(argument, "Positional after named argument")
      case ParameterSpecifiedMultipleTimes(assignment) =>
        holder.createErrorAnnotation(assignment.leftExpression, "Parameter specified multiple times")
      case WrongTypeParameterInferred => //todo: ?
      case ExpectedTypeMismatch => //will be reported later
      case DefaultTypeParameterMismatch(expected, actual) => constructor.typeArgList match {
        case Some(tpArgList) =>
          val message: String = ScalaBundle.message("type.mismatch.default.args.expected.actual", expected, actual)
          holder.createErrorAnnotation(tpArgList, message)
        case _ =>
      }
      case _ => holder.createErrorAnnotation(argsElements, s"Not applicable. constructor$signature")
    }
  }

  private def parameterToArgClause(p: Parameter, constructorDef: ScMethodLike, argClauses: Seq[ScArgumentExprList]): Option[ScArgumentExprList] = {
    p.psiParam.flatMap {param =>
      // look into every parameter list and find param
      val idx = constructorDef.parameterList.clauses.indexWhere( clause =>
        clause.parameters.contains(param)
      )
      argClauses.lift(idx)
    }
  }

  private def isConstructorMalformed(r: ScalaResolveResult): Boolean =
    r.problems.exists { case MalformedDefinition() => true; case _ => false }

  private def argsElementsTextRange(constructor: ScConstructor): TextRange = constructor.arguments match {
    case head +: tail => tail.foldLeft(head.getTextRange)(_ union _.getTextRange)
    case _ => constructor.getTextRange
  }

  private object ScConstructorResolveResult {
    def unapply(arg: ScalaResolveResult): Option[ScMethodLike] =
      Some(arg.element).collect { case constr: ScMethodLike => constr }
  }
}
