package org.jetbrains.plugins.scala.lang.findUsages

import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTreeUtil.{getParentOfType, isAncestor}
import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement}
import com.intellij.usages.impl.rules.UsageType._
import com.intellij.usages.impl.rules.{UsageType, UsageTypeProviderEx}
import com.intellij.usages.{PsiElementUsageTarget, UsageTarget}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.MethodValue
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSelfTypeElement, ScTypeArgs, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{AuxiliaryConstructor, ScAccessModifier, ScAnnotationExpr, ScConstructorInvocation, ScLiteral, ScPrimaryConstructor, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScParameterClause, ScParameters, ScTypeParam, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
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
  override def getUsageType(element: PsiElement, targets: Array[UsageTarget]): UsageType = {
    element.getContainingFile match {
      case _: ScalaFile =>
      case _ =>
        return null
    }

    // Early return null for some elements for which there is no point to calculate usage type
    // Primarily needed for tests. Note that Java implementation might still fall back to "Read" usage type
    if (!shouldCalculateUsage(element))
      return null

    val result = (element, targets) match {
      case (referenceElement: ScReference, Array(only: PsiElementUsageTarget))
        if isConstructorPatternReference(referenceElement) && !referenceElement.isReferenceTo(only.getElement) =>
        Some(ParameterInPattern)
      case (SAMTypeImplementation(_), _) if isSAMTypeUsageTarget(targets) =>
        Option(SAMImplementation)
      case (_: UnresolvedImplicitFakePsiElement, _) =>
        Option(UnresolvedImplicit)
      case (e, Array(target: PsiElementUsageTarget))
        if isImplicitUsageTarget(target) && isReferencedImplicitlyIn(target.getElement, e) =>
        Some(ImplicitConversionOrParam)
      case (referenceElement: ScReference, Array(only: PsiElementUsageTarget))
        if isConstructorPatternReference(referenceElement) && !referenceElement.isReferenceTo(only.getElement) =>
        Some(ParameterInPattern)
      case _ =>
        //TODO: Only run this logic for references or leaf elements?
        element.withParentsInFile
          .flatMap(usageType(_, element))
          .nextOption()
    }
    result.orNull
  }
}

object ScalaUsageTypeProvider {

  /**
   * Ror some elements it doesn't make sense to calculate usage type as it can't be found in "find usages"
   * This is primarily needed for tests, but I put the logic here in order unit tests test actual prod logic
   * It shouldn't' hurt production logic.
   *
   * @note Current logic is "excluding" - it allows all elements except cases in the "block-list".
   *       Alternatively, it could be "including" - allow only those elements which are in the "white-list" (e.g. everything which is a reference).
   *       But I decided not to go this way as we might miss some test cases.
   *       With "block-list" you can simply add a new element if you notice any annoying redundant usage type in the test data
   */
  private def shouldCalculateUsage(element: PsiElement) = element match {
    case _: LeafPsiElement |
         _: ScLiteral |

         // block-like structures
         _: ScTry |
         _: ScCatchBlock |
         _: ScFinallyBlock |
         _: ScBlock |
         _: ScTemplateBody |
         _: ScEarlyDefinitions |

         _: ScCaseClause |
         _: ScCaseClauses |

         _: ScArgumentExprList |
         _: ScParameterClause |
         _: ScParameters |
         _: ScTypeParamClause => false
    case _ => true
  }

  private def isSAMTypeUsageTarget(usageTargets: Array[UsageTarget]): Boolean = {
    val psiElements = usageTargets.collect { case psiUsageTarget: PsiElementUsageTarget => psiUsageTarget.getElement }
    psiElements.exists {
      case cls: PsiClass => cls.isSAMable
      case _ => false
    }
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

  def referenceExpressionUsageType(referenceExpr: ScReferenceExpression): Option[UsageType] = {
    val referenceResolved: Option[PsiNamedElement] = referenceExpr.bind().map(_.element)
    referenceResolved.flatMap {
      case function: ScFunction if function.isApplyMethod =>
        Some(MethodApply)
      case _: ScPrimaryConstructor | AuxiliaryConstructor(_) =>
        //Handle Scala 3 "Universal Apply Methods" (https://docs.scala-lang.org/scala3/reference/other-new-features/creator-applications.html)
        //Even though there is no "new" keyword, it seems we should place usages in same group
        //So `new MyClass()` and `MyClass()` (when it's not invocation of apply method) should be placed in same group
        //Note, on UI it will be called "New instance creation", "new" keyword won't be mentioned
        Some(CLASS_NEW_OPERATOR)
      case definition: ScFunctionDefinition if isAncestor(definition, referenceExpr, false) =>
        Some(RECURSION)
      case definition: ScFunctionDefinition =>
        val qualifier = referenceExpr.qualifier
        qualifier.flatMap {
          case _: ScSuperReference =>
            //NOTE: In Java they also support DELEGATE_TO_SUPER_PARAMETERS_CHANGED, DELEGATE_TO_ANOTHER_INSTANCE, DELEGATE_TO_ANOTHER_INSTANCE_PARAMETERS_CHANGED
            //We don't do it here due to performance reasons. Implementation would require traversal of class signatures
            //in many found places, in Scala it might cause some performance issues and should be done very carefully
            val containerDefinition = PsiTreeUtil.getParentOfType(referenceExpr, classOf[ScFunctionDefinition], classOf[ScPatternDefinition])
            val isDelegateToSuper = !containerDefinition.isLocal && hasSameName(definition, containerDefinition)
            if (isDelegateToSuper) Some(DELEGATE_TO_SUPER) else None
          case _ =>
            None
        }
      case _ =>
        None
    }
  }

  //TODO: handle @targetName in Scala 3?
  private def hasSameName(baseMethod: ScFunctionDefinition, member: ScMember) =
    member match {
      case f: ScFunctionDefinition =>
        f.name == baseMethod.name
      case v: ScPatternDefinition =>
        v.declaredNames match {
          case Seq(singleName) => singleName == baseMethod.name
          case _ =>
            //NOTE: we ignore with multiple definitions in same pattern: `override val (foo1, foo2) = (super.foo1, super.foo2)`
            // This is done for the simplicity of current implementation
            false
        }
      case _ =>
        false
    }

  def patternUsageType(pattern: ScPattern): UsageType = {
    def isPatternAncestor(element: PsiElement) = isAncestor(pattern, element, false)

    val catchClausePatterns = pattern.parentOfType(classOf[ScCatchBlock]).toSeq
      .collect {  case ScCatchBlock(clauses) => clauses }
      .flatMap(_.caseClauses)
      .flatMap(_.pattern)

    if (catchClausePatterns.exists(isPatternAncestor))
      CLASS_CATCH_CLAUSE_PARAMETER_DECLARATION
    else pattern match {
      case _: ScConstructorPattern | _: ScInfixPattern =>
        Extractor //Q: maybe we should remove separate "Extractor" pattern and just always use "Pattern"?
      case _ =>
        Pattern
    }
  }

  implicit def stringToUsageType(@Nls name: String): UsageType = new UsageType(() => name)
  val Extractor: UsageType                 = ScalaBundle.message("usage.extractor")
  val Pattern: UsageType                   = ScalaBundle.message("usage.pattern")
  val TypedExpression: UsageType           = ScalaBundle.message("usage.typed.statement")
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

  //TODO: handle Scala 3 universal apply syntax
  private def usageTypeOfConstructorInvocation(constructorInvocation: ScConstructorInvocation): UsageType = {
    val templateDefinition = constructorInvocation.templateDefinitionContext
    templateDefinition match {
      //We are only interested in first constructor invocation (`MyClass` in `new MyClass with MyTrait`, but not `MyTrait`)
      case Some(newTd: ScNewTemplateDefinition) if newTd.firstConstructorInvocation.contains(constructorInvocation) =>
        if (newTd.extendsBlock.isAnonymousClass) CLASS_ANONYMOUS_NEW_OPERATOR else CLASS_NEW_OPERATOR
      case _ =>
        constructorInvocation.getParent match {
          case _: ScAnnotationExpr =>
            ANNOTATION
          case _ =>
            CLASS_EXTENDS_IMPLEMENTS_LIST
        }
    }
  }

  private[this] def nullableUsageType(element: PsiElement, original: PsiElement): UsageType = {
    def isAppropriate(parent: PsiElement): Boolean = isAncestor(parent, original, false)

    def existsAppropriate(maybeParent: Option[PsiElement]): Boolean = maybeParent.exists(isAppropriate)

    element match {
      case _: ScImportExpr => CLASS_IMPORT
      case typeArgs: ScTypeArgs => typeArgsUsageType(typeArgs)
      case constructorInvocation: ScConstructorInvocation =>
        usageTypeOfConstructorInvocation(constructorInvocation)
      case _: ScParameter => CLASS_METHOD_PARAMETER_DECLARATION
      case pattern: ScPattern => patternUsageType(pattern)
      case typeElement: ScTypeElement => typeUsageType(typeElement)
      case _: ScInterpolatedExpressionPrefix => PrefixInterpolatedString
      case expression: ScReferenceExpression => referenceExpressionUsageType(expression).orNull
      case reference: ScThisReference if existsAppropriate(reference.reference) => ThisReference
      case _: ScAccessModifier => AccessModifier
      case packaging: ScPackaging if existsAppropriate(packaging.reference) => PackageClause
      case assignment: ScAssignment if isAppropriate(assignment.leftExpression) =>
        if (assignment.isNamedParameter) NamedParameter else WRITE
      case MethodValue(_)                                                         => FunctionExpression
      case _: ScBlock | _: ScTemplateBody | _: ScEarlyDefinitions | _: ScArgumentExprList =>
        //Stop processing parents early, consider usage type to be just "READ"
        READ
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
