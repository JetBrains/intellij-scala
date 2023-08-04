package org.jetbrains.plugins.scala.lang.findUsages

import com.intellij.psi.util.PsiTreeUtil.{getParentOfType, isAncestor}
import com.intellij.psi.{PsiClass, PsiElement}
import com.intellij.usages.impl.rules.UsageType._
import com.intellij.usages.impl.rules.{UsageType, UsageTypeProviderEx}
import com.intellij.usages.{PsiElementUsageTarget, UsageTarget}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.MethodValue
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSelfTypeElement, ScTypeArgs, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAccessModifier, ScAnnotation, ScAnnotationExpr, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScTemplateBody, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScPackaging}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedExpressionPrefix
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.util.ImplicitUtil._
import org.jetbrains.plugins.scala.util.SAMUtil._

import scala.language.implicitConversions

final class ScalaUsageTypeProvider extends UsageTypeProviderEx {

  import ScalaUsageTypeProvider._

  override def getUsageType(element: PsiElement): UsageType =
    getUsageType(element, UsageTarget.EMPTY_ARRAY)

  // TODO more of these, including Scala specific: case class/object, pattern match, type ascription, ...
  override def getUsageType(element: PsiElement, targets: Array[UsageTarget]): UsageType =
    element.containingScalaFile.flatMap { _ =>
      (element, targets) match {
        case (referenceElement: ScReference, Array(only: PsiElementUsageTarget))
          if isConstructorPatternReference(referenceElement) && !referenceElement.isReferenceTo(only.getElement) =>
          Some(ParameterInPattern)
        case (SAMTypeImplementation(_), _) if isSAMTypeUsageTarget(targets) =>
          Option(SAMImplementation)
        case (_: UnresolvedImplicitFakePsiElement, _) => Option(UnresolvedImplicit)
        case (e, Array(target: PsiElementUsageTarget))
          if isImplicitUsageTarget(target) && isReferencedImplicitlyIn(target.getElement, e) =>
          Some(ImplicitConversionOrParam)
        case (referenceElement: ScReference, Array(only: PsiElementUsageTarget))
          if isConstructorPatternReference(referenceElement) && !referenceElement.isReferenceTo(only.getElement) =>
          Some(ParameterInPattern)
        case _ =>
          element.withParentsInFile
            .flatMap(usageType(_, element))
            .nextOption()
      }
    }.orNull
}

object ScalaUsageTypeProvider {
  private def isSAMTypeUsageTarget(t: Array[UsageTarget]): Boolean =
    t.collect { case psiUsageTarget: PsiElementUsageTarget => psiUsageTarget.getElement }
      .exists {
        case cls: PsiClass => cls.isSAMable
        case _             => false
      }

  private def isImplicitUsageTarget(target: PsiElementUsageTarget): Boolean = target.getElement match {
    case ImplicitSearchTarget(_) => true
    case _                       => false
  }

  private def isReferencedImplicitlyIn(target: PsiElement, e: PsiElement): Boolean =
    target.refOrImplicitRefIn(e) match {
      case Some(_: ImplicitReference) => true
      case _                          => false
    }

  def referenceExpressionUsageType(expression: ScReferenceExpression): UsageType = {
    def resolvedElement(result: ScalaResolveResult) =
      result.innerResolveResult
        .getOrElse(result).element

    expression.bind()
      .map(resolvedElement)
      .collect {
        case function: ScFunction if function.isApplyMethod => MethodApply
        case definition: ScFunctionDefinition if isAncestor(definition, expression, false) => RECURSION
      }.orNull
  }

  def patternUsageType(pattern: ScPattern): UsageType = {
    def isPatternAncestor(element: PsiElement) = isAncestor(element, pattern, false)

    val patterns = pattern.parentOfType(classOf[ScCatchBlock]).toSeq.collect {
      case ScCatchBlock(clauses) => clauses
    }.flatMap(_.caseClauses)
      .flatMap(_.pattern)

    if (patterns.exists(isPatternAncestor)) CLASS_CATCH_CLAUSE_PARAMETER_DECLARATION
    else pattern match {
      case ScTypedPatternLike(typePattern) if isPatternAncestor(typePattern.typeElement) => ClassTypedPattern
      case _: ScConstructorPattern | _: ScInfixPattern => Extractor
      case _ => null
    }
  }

  implicit def stringToUsageType(@Nls name: String): UsageType = new UsageType(() => name)
  val Extractor: UsageType                 = ScalaBundle.message("usage.extractor")
  val ClassTypedPattern: UsageType         = ScalaBundle.message("usage.typed.pattern")
  val TypedExpression: UsageType            = ScalaBundle.message("usage.typed.statement")
  val MethodApply: UsageType               = ScalaBundle.message("usage.method.apply")
  val ThisReference: UsageType             = ScalaBundle.message("usage.this.reference")
  val AccessModifier: UsageType            = ScalaBundle.message("usage.access.modifier")
  val PackageClause: UsageType             = ScalaBundle.message("usage.package.clause")
  val FunctionExpression: UsageType        = ScalaBundle.message("usage.function.expression")
  val NamedParameter: UsageType            = ScalaBundle.message("usage.named.parameter")
  val PrefixInterpolatedString: UsageType  = ScalaBundle.message("usage.interpolated.string.prefix")
  val ParameterInPattern: UsageType        = ScalaBundle.message("usage.parameter.in.pattern")
  val SelfType: UsageType                  = ScalaBundle.message("usage.self.type")
  val TypeBound: UsageType                 = ScalaBundle.message("usage.type.bound")
  val TypeAlias: UsageType                 = ScalaBundle.message("usage.type.alias")
  val SecondaryConstructor: UsageType      = ScalaBundle.message("usage.secondary.constructor")
  val ImplicitConversionOrParam: UsageType = ScalaBundle.message("usage.implicit.conversion.parameter")
  val UnresolvedImplicit: UsageType        = ScalaBundle.message("usage.unresolved.implicit.conversion.parameter")
  val SAMImplementation: UsageType         = ScalaBundle.message("usage.sam.interface.implementation")

  private def usageType(element: PsiElement, original: PsiElement): Option[UsageType] =
    Option(nullableUsageType(element, original))

  private def isConstructorPatternReference(element: ScReference): Boolean = element.resolve() match {
    case pattern: ScBindingPattern => getParentOfType(pattern, classOf[ScConstructorPattern], classOf[ScInfixPattern]) != null
    case _ => false
  }

  private[this] def nullableUsageType(element: PsiElement, original: PsiElement): UsageType = {
    def isAppropriate(parent: PsiElement): Boolean = isAncestor(parent, original, false)

    def existsAppropriate(maybeParent: Option[PsiElement]): Boolean = maybeParent.exists(isAppropriate)

    element match {
      case _: ScImportExpr => CLASS_IMPORT
      case typeArgs: ScTypeArgs => typeArgsUsageType(typeArgs)
      case templateParents: ScTemplateParents => templateParentsUsageType(templateParents)
      case _: ScParameter => CLASS_METHOD_PARAMETER_DECLARATION
      case pattern: ScPattern => patternUsageType(pattern)
      case typeElement: ScTypeElement => typeUsageType(typeElement)
      case _: ScInterpolatedExpressionPrefix => PrefixInterpolatedString
      case expression: ScReferenceExpression => referenceExpressionUsageType(expression)
      case expression: ScAnnotationExpr if existsAppropriate(expression.constructorInvocation.reference) => ANNOTATION
      case reference: ScThisReference if existsAppropriate(reference.reference) => ThisReference
      case reference: ScSuperReference if existsAppropriate(reference.reference) => DELEGATE_TO_SUPER
      case _: ScAccessModifier => AccessModifier
      case packaging: ScPackaging if existsAppropriate(packaging.reference) => PackageClause
      case assignment: ScAssignment if isAppropriate(assignment.leftExpression) =>
        if (assignment.isNamedParameter) NamedParameter else WRITE
      case MethodValue(_)                                                         => FunctionExpression
      case _: ScBlock | _: ScTemplateBody | _: ScEarlyDefinitions                 => READ
      case invocation: ScSelfInvocation if !isAppropriate(invocation.args.orNull) => SecondaryConstructor
      case _                                                                      => null
    }
  }

  private[this] def typeArgsUsageType(typeArguments: ScTypeArgs): UsageType =
    Option(typeArguments.getParent).collect {
      case ScGenericCall(reference, Seq(_)) => reference.refName
    }.collect {
      case "isInstanceOf" => CLASS_INSTANCE_OF
      case "asInstanceOf" => CLASS_CAST_TO
      case "classOf" => CLASS_CLASS_OBJECT_ACCESS
    }.getOrElse(TYPE_PARAMETER)

  private[this] def templateParentsUsageType(tp: ScTemplateParents): UsageType =
    getParentOfType(tp, classOf[ScTemplateDefinition], classOf[ScAnnotation]) match {
      case templateDefinition: ScNewTemplateDefinition =>
        if (templateDefinition.extendsBlock.isAnonymousClass) CLASS_ANONYMOUS_NEW_OPERATOR else CLASS_NEW_OPERATOR
      case _: ScTemplateDefinition => CLASS_EXTENDS_IMPLEMENTS_LIST
      case _ => null
    }

  private[this] def typeUsageType(typeElement: ScTypeElement): UsageType = {
    def isAppropriate(maybeTypeElement: Option[ScTypeElement]) = maybeTypeElement.contains(typeElement)

    typeElement.getParent match {
      case function: ScFunction if isAppropriate(function.returnTypeElement) =>
        CLASS_METHOD_RETURN_TYPE
      case valueOrVariable: ScValueOrVariable if isAppropriate(valueOrVariable.typeElement) =>
        if (valueOrVariable.isLocal) CLASS_LOCAL_VAR_DECLARATION else CLASS_FIELD_DECLARATION
      case classParameter: ScClassParameter if isAppropriate(classParameter.typeElement) && classParameter.isClassMember =>
        CLASS_FIELD_DECLARATION
      case typedExpr: ScTypedExpression if isAppropriate(typedExpr.typeElement) =>
        TypedExpression
      case _: ScSelfTypeElement => SelfType
      case _: ScTypeAliasDeclaration | _: ScTypeParam => TypeBound
      case _: ScTypeAliasDefinition => TypeAlias
      case _ => null
    }
  }
}
