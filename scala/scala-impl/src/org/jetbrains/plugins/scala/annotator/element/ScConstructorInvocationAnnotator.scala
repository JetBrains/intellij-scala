package org.jetbrains.plugins.scala.annotator.element

import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiComment, PsiNamedElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.registerTypeMismatchError
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.annotator.template.ImplicitParametersAnnotator
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScLiteralTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ConstructorInvocationLike, ScConstructorInvocation, ScMethodLike, ScalaConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScArgumentExprList
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScConstructorOwner, ScEnum, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.UndefinedType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectContext

// TODO unify with ScMethodInvocationAnnotator and ScReferenceAnnotator
object ScConstructorInvocationAnnotator extends ElementAnnotator[ScConstructorInvocation] {

  override def annotate(element: ScConstructorInvocation, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    if (typeAware) {
      val isInTraitDefinition = element.templateDefinitionContext.exists(_.is[ScTrait])
      if (!isInTraitDefinition) {
        ImplicitParametersAnnotator.annotate(element, typeAware)
        annotateConstructorInvocation(element)
      }
    }
  }

  // TODO duplication with application annotator.
  private def annotateConstructorInvocation(constrInvocation: ScConstructorInvocation)
                                           (implicit holder: ScalaAnnotationHolder): Unit = {
    constrInvocation.typeElement match {
      case lit: ScLiteralTypeElement =>
        val message = ScalaBundle.message("annotator.error.class.type.required.but.found", lit)
        holder.createErrorAnnotation(constrInvocation.typeElement, message)
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
      // (wrong! they can now in scala 3, that's why we are checking if the element is
      //  a constructorOwner AND also has a constructor! It will not have one in scala 2)
      mayBeConstructor = element.asOptionOf[ScConstructorOwner].flatMap(_.constructor)
      elementShouldHaveBeenConcreteConstructor = mayBeConstructor.isDefined
      accessible = resolveResult.isAccessible && !elementShouldHaveBeenConcreteConstructor
    } yield resolveResult.copy(isAccessible = accessible) // TODO Resolve should return matching inaccessible constructors, SCL-22156

    if (resolved.exists(isConstructorMalformed)) {
      val message = ScalaBundle.message("annotator.error.constructor.has.malformed.definition")
      holder.createErrorAnnotation(constrInvocation.typeElement, message)
    }

    resolved match {
      case Seq(r) if !r.isAccessible => holder.createErrorAnnotation(argsElementsTextRange(constrInvocation), ScalaBundle.message("annotator.error.no.constructor.accessible"))
      case Seq(r@ScConstructorResolveResult(constr)) if constr.effectiveParameterClauses.length > 1 && !isConstructorMalformed(r) =>
        val params   = constr.getClassTypeParameters.map(_.typeParameters).getOrElse(Seq.empty)
        val typeArgs = constrInvocation.typeArgList.map(_.typeArgs).getOrElse(Seq.empty)

        val substitutor =
          ScSubstitutor
            .bind(params, typeArgs)(_.calcType)
            .followed(ScSubstitutor.bind(params)(UndefinedType(_)))
            .followed(r.substitutor)

        val res =
          Compatibility.checkConstructorApplicability(
            constrInvocation,
            substitutor,
            constrInvocation.arguments,
            constr.effectiveParameterClauses
          )(constr.projectContext)

        annotateProblems(res.problems, r, constrInvocation)
      case results if results.length > 1 =>
        val message = ScalaBundle.message("annotator.error.cannot.resolve.overloaded.constructor", constrInvocation.typeElement.getText)
        holder.createErrorAnnotation(constrInvocation.typeElement, message)
      case _ =>
        for (r <- resolved)
          annotateProblems(r.problems, r, constrInvocation)
    }
  }


  def isJavaEnumExtendedByScalaEnum(target: PsiNamedElement, invocation: ConstructorInvocationLike): Boolean =
    invocation.asOptionOf[ScConstructorInvocation].flatMap(_.templateDefinitionContext).exists(_.is[ScEnum]) && {
      val cls = PsiTreeUtil.getContextOfType(target, classOf[PsiClass], false)
      cls != null && cls.qualifiedName == "java.lang.Enum"
    }

  def annotateProblems(problems: Seq[ApplicabilityProblem], r: ScalaResolveResult, constrInvocation: ConstructorInvocationLike)
                      (implicit holder: ScalaAnnotationHolder): Unit = if (!isJavaEnumExtendedByScalaEnum(r.element, constrInvocation)) {
    val element = r.element
    def argsElements = argsElementsTextRange(constrInvocation)
    // TODO decouple
    def nameWithSignature = ScReferenceAnnotator.nameWithSignature(element)

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

    implicit val tpc: TypePresentationContext = TypePresentationContext(element)
    missedParamsPerArgList.foreach {
      case (param, missing) =>
        val range = param.map { argumentList =>
          argumentList.exprs.lastOption
            .map(e => new TextRange(e.getTextRange.getEndOffset - 1, argumentList.getTextRange.getEndOffset))
            .getOrElse(argumentList.getTextRange)
        } getOrElse {
          argsElements
        }

        val paramsText = missing.map(p => p.name + ": " + p.paramType.presentableText).mkString(", ")
        holder.createErrorAnnotation(range,
          ScalaBundle.message("annotator.error.unspecified.value.parameters", paramsText))
    }

    // check if the found element can even be used as a constructor
    (element, constrInvocation.arguments) match {
      case (tr: ScTrait, head +: tail) if head.exprs.nonEmpty || tail.nonEmpty =>
        // new Trait() {} is allowed!
        // but not   new Trait()() {}
        // or        new Trait(i: Int) {}
        holder.createErrorAnnotation(tail.foldLeft(head.getTextRange)(_ union _.getTextRange),
          ScalaBundle.message("annotator.error.trait.has.no.constructor", tr.name))
      case _ =>
    }

    val countMatches = !problems.exists(_.is[MissedValueParameter, ExcessArgument])

    var typeMismatchShown = false

    val firstExcessiveArgument = problems.filterByType[ExcessArgument].map(_.argument).minByOption(_.getTextOffset)
    firstExcessiveArgument.foreach { argument =>
      val opening = argument.prevSiblings.takeWhile(e => e.is[PsiWhiteSpace, PsiComment] || e.textMatches(",") || e.textMatches("(")).lastOption
      val range = opening.map(e => new TextRange(e.getTextOffset, argument.getTextOffset + 1)).getOrElse(argument.getTextRange)
      val message = ScalaBundle.message("annotator.error.too.many.arguments.for.constructor", nameWithSignature)
      holder.createErrorAnnotation(range, message)
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
        val message = ScalaBundle.message("annotator.error.missing.argument.list.for.constructor", nameWithSignature)
        holder.createErrorAnnotation(markRange, message)
      case MissedValueParameter(_) => // simultaneously handled above
      case UnresolvedParameter(_) => // don't show function inapplicability, unresolved
      case MalformedDefinition(_) => // handled before to avoid duplications
      case ExpansionForNonRepeatedParameter(expression) =>
        val message = ScalaBundle.message("annotator.error.expansion.for.non.repeated.parameter")
        holder.createErrorAnnotation(expression, message)
      case PositionalAfterNamedArgument(argument) =>
        val message = ScalaBundle.message("annotator.error.positional.after.named.argument")
        holder.createErrorAnnotation(argument, message)
      case ParameterSpecifiedMultipleTimes(assignment) =>
        val message = ScalaBundle.message("annotator.error.parameter.specified.multiple.times")
        holder.createErrorAnnotation(assignment.leftExpression, message)
      case WrongTypeParameterInferred => //todo: ?
      case ExpectedTypeMismatch => //will be reported later
      case DefaultTypeParameterMismatch(expected, actual) => constrInvocation.typeArgList match {
        case Some(tpArgList) =>
          val message: String = ScalaBundle.message("type.mismatch.default.args.expected.actual", expected, actual)
          holder.createErrorAnnotation(tpArgList, message)
        case _ =>
      }
      case _ =>
        val message = ScalaBundle.message("annotator.error.cannot.apply.constructor", nameWithSignature)
        holder.createErrorAnnotation(argsElements, message)
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
    r.problems.exists { case MalformedDefinition(_) => true; case _ => false }

  def argsElementsTextRange(constrInvocation: ConstructorInvocationLike): TextRange = constrInvocation.arguments match {
    case head +: tail => tail.foldLeft(head.getTextRange)(_ union _.getTextRange)
    case _ => constrInvocation.getTextRange
  }

  object ScConstructorResolveResult {
    def unapply(res: ScalaResolveResult): Option[ScMethodLike] =
      Option(res.element).collect { case constr: ScMethodLike => constr }
  }
}

