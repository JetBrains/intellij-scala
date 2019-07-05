package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiComment, PsiWhiteSpace}
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.registerTypeMismatchError
import org.jetbrains.plugins.scala.annotator.template.ImplicitParametersAnnotator
import org.jetbrains.plugins.scala.lang.psi.api.ConstructorInvocationLike
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScLiteralTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructorInvocation, ScMethodLike, ScalaConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScArgumentExprList
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScConstructorOwner, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.types.api.UndefinedType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{ApplicabilityProblem, Compatibility, DefaultTypeParameterMismatch, ExcessArgument, ExpansionForNonRepeatedParameter, ExpectedTypeMismatch, MalformedDefinition, MissedParametersClause, MissedValueParameter, ParameterSpecifiedMultipleTimes, PositionalAfterNamedArgument, TypeMismatch, UnresolvedParameter, WrongTypeParameterInferred}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.extensions._

// TODO unify with ScMethodInvocationAnnotator and ScReferenceAnnotator
object ScConstructorInvocationAnnotator extends ElementAnnotator[ScConstructorInvocation] {

  override def annotate(element: ScConstructorInvocation, typeAware: Boolean = true)
                       (implicit holder: AnnotationHolder): Unit = {
    if (typeAware) {
      ImplicitParametersAnnotator.annotate(element, typeAware)
      annotateConstructorInvocation(element)
    }
  }

  // TODO duplication with application annotator.
  private def annotateConstructorInvocation(constrInvocation: ScConstructorInvocation)
                                           (implicit holder: AnnotationHolder) {
    constrInvocation.typeElement match {
      case lit: ScLiteralTypeElement =>
        holder.createErrorAnnotation(constrInvocation.typeElement, s"Class type required but ($lit) found")
      case _ =>
    }
    //in case if constructor is function
    constrInvocation.reference match {
      case None => return
      case _ =>
    }

    val resolved = for {
      reference <- constrInvocation.reference.toList
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
      holder.createErrorAnnotation(constrInvocation.typeElement, "Constructor has malformed definition")
    }

    resolved match {
      case Seq() => holder.createErrorAnnotation(argsElementsTextRange(constrInvocation), s"No constructor accessible from here")
      case Seq(r@ScConstructorResolveResult(constr)) if constr.effectiveParameterClauses.length > 1 && !isConstructorMalformed(r) =>
        // if there is only one well-formed, resolved, scala constructor with multiple parameter clauses,
        // check all of these clauses
        implicit val ctx: ProjectContext = constr

        val params = constr.getClassTypeParameters.map(_.typeParameters).getOrElse(Seq.empty)
        val typeArgs = constrInvocation.typeArgList.map(_.typeArgs).getOrElse(Seq.empty)
        val substitutor = ScSubstitutor.bind(params, typeArgs)(_.calcType)
          .followed(ScSubstitutor.bind(params)(UndefinedType(_)))
          .followed(r.substitutor)

        val res = Compatibility.checkConstructorConformance(
          constrInvocation,
          substitutor,
          constrInvocation.arguments,
          constr.effectiveParameterClauses
        )

        annotateProblems(res.problems, r, constrInvocation)
      case results if results.length > 1 =>
        holder.createErrorAnnotation(constrInvocation.typeElement, s"Cannot resolve overloaded constructor `${constrInvocation.typeElement.getText}`")
      case _ =>
        for (r <- resolved)
          annotateProblems(r.problems, r, constrInvocation)
    }
  }

  def annotateProblems(problems: Seq[ApplicabilityProblem], r: ScalaResolveResult, constrInvocation: ConstructorInvocationLike)
                      (implicit holder: AnnotationHolder): Unit = {
    val element = r.element
    def argsElements = argsElementsTextRange(constrInvocation)
    // TODO decouple
    def signature = ScReferenceAnnotator.signatureOf(element)

    // mark problematic clauses where parameters are missing
    val missedParams = problems.collect { case MissedValueParameter(p) => p}
    val missedParamsPerArgList = element match {
      case ScalaConstructor(constr) =>
        missedParams.groupBy(parameterToArgClause(_, constr, constrInvocation.arguments))
      case _ if missedParams.nonEmpty =>
        Map(None -> missedParams)
      case _ =>
        Map.empty
    }

    missedParamsPerArgList.foreach {
      case (param, missing) =>
        val range = param.map { argumentList =>
          argumentList.exprs.lastOption
            .map(e => new TextRange(e.getTextRange.getEndOffset - 1, argumentList.getTextRange.getEndOffset))
            .getOrElse(argumentList.getTextRange)
        } getOrElse {
          argsElements
        }

        holder.createErrorAnnotation(range,
          "Unspecified value parameters: " + missing.map(p => p.name + ": " + p.paramType.presentableText).mkString(", "))
    }

    // check if the found element can even be used as a constructor
    (element, constrInvocation.arguments) match {
      case (tr: ScTrait, head +: tail) if head.exprs.nonEmpty || tail.nonEmpty =>
        // new Trait() {} is allowed!
        // but not   new Trait()() {}
        // or        new Trait(i: Int) {}
        holder.createErrorAnnotation(tail.foldLeft(head.getTextRange)(_ union _.getTextRange),
          s"${tr.name} is a trait and thus has no constructor")
      case _ =>
    }

    val countMatches = !problems.exists(_.is[MissedValueParameter, ExcessArgument])

    var typeMismatchShown = false

    val firstExcessiveArgument = problems.filterBy[ExcessArgument].map(_.argument).firstBy(_.getTextOffset)
    firstExcessiveArgument.foreach { argument =>
      val opening = argument.prevSiblings.takeWhile(e => e.is[PsiWhiteSpace] || e.is[PsiComment] || e.textMatches(",") || e.textMatches("(")).toSeq.lastOption
      val range = opening.map(e => new TextRange(e.getTextOffset, argument.getTextOffset + 1)).getOrElse(argument.getTextRange)
      holder.createErrorAnnotation(range, s"Too many arguments for constructor$signature")
    }

    problems.foreach {
      case ExcessArgument(_) =>  // simultaneously handled above
      case TypeMismatch(expression, expectedType) =>
        if (countMatches && !typeMismatchShown) {
          expression.`type`().foreach {
            registerTypeMismatchError(_, expectedType, expression)
          }
          typeMismatchShown = true
        }
      case MissedParametersClause(_) =>
        // try to mark the right-most bracket
        val markRange = constrInvocation.arguments.lastOption
          .map(_.getTextRange.getEndOffset)
          .map(off => TextRange.create(off - 1, off))
          .getOrElse(constrInvocation.getTextRange)
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
      case DefaultTypeParameterMismatch(expected, actual) => constrInvocation.typeArgList match {
        case Some(tpArgList) =>
          val message: String = ScalaBundle.message("type.mismatch.default.args.expected.actual", expected, actual)
          holder.createErrorAnnotation(tpArgList, message)
        case _ =>
      }
      case _ => holder.createErrorAnnotation(argsElements, s"Not applicable. constructor$signature")
    }
  }

  def parameterToArgClause(p: Parameter, constr: ScMethodLike, argClauses: Seq[ScArgumentExprList]): Option[ScArgumentExprList] = {
    p.psiParam.flatMap { param =>
      // look into every parameter list and find param
      val idx = constr.parameterList.clauses.indexWhere( clause =>
        clause.parameters.contains(param)
      )
      argClauses.lift(idx)
    }
  }

  def isConstructorMalformed(r: ScalaResolveResult): Boolean =
    r.problems.exists { case MalformedDefinition() => true; case _ => false }

  def argsElementsTextRange(constrInvocation: ConstructorInvocationLike): TextRange = constrInvocation.arguments match {
    case head +: tail => tail.foldLeft(head.getTextRange)(_ union _.getTextRange)
    case _ => constrInvocation.getTextRange
  }

  object ScConstructorResolveResult {
    def unapply(res: ScalaResolveResult): Option[ScMethodLike] =
      Some(res.element).collect { case constr: ScMethodLike => constr }
  }
}

