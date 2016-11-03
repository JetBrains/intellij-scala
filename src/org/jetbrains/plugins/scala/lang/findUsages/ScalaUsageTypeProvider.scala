package org.jetbrains.plugins.scala
package lang
package findUsages

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usages.impl.rules.{UsageType, UsageTypeProviderEx}
import com.intellij.usages.{PsiElementUsageTarget, UsageTarget}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.MethodValue
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSelfTypeElement, ScTypeArgs, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAccessModifier, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScTemplateBody, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScPackaging}
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScInterpolatedStringPartReference
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.language.implicitConversions

final class ScalaUsageTypeProvider extends UsageTypeProviderEx {
  def getUsageType(element: PsiElement): UsageType = getUsageType(element, null)

  def getUsageType(element: PsiElement, targets: Array[UsageTarget]): UsageType = {
    import ScalaUsageTypeProvider._
    import UsageType._

    def isAncestor(parent: PsiElement): Boolean = PsiTreeUtil.isAncestor(parent, element, false)

    def isSomeAncestor(parent: Option[PsiElement]): Boolean = parent.exists(PsiTreeUtil.isAncestor(_, element, false))

    def typeArgsUsageType(ta: ScTypeArgs) = ta match {
      case ChildOf(ScGenericCall(ref: ScReferenceExpression, Seq(_))) =>
        ref.refName match {
          case "isInstanceOf" => CLASS_INSTANCE_OF
          case "asInstanceOf" => CLASS_CAST_TO
          case "classOf" => CLASS_CLASS_OBJECT_ACCESS
          case _ => TYPE_PARAMETER
        }
      case _ => TYPE_PARAMETER
    }

    def templateParentsUsageType(tp: ScTemplateParents) = {
      ScalaPsiUtil.getParentOfType(tp, classOf[ScTemplateDefinition], classOf[ScAnnotation]) match {
        case newTd: ScNewTemplateDefinition =>
          if (newTd.extendsBlock.isAnonymousClass) Some(CLASS_ANONYMOUS_NEW_OPERATOR)
          else Some(CLASS_NEW_OPERATOR)
        case _: ScTemplateDefinition => Some(CLASS_EXTENDS_IMPLEMENTS_LIST)
        case _ => None
      }
    }

    def refExprUsage(ref: ScReferenceExpression) = {
      val rr = ref.bind().map(srr => srr.innerResolveResult.getOrElse(srr))

      rr match {
        case Some(ScalaResolveResult(fun: ScFunction, _)) if fun.name == "apply" && ref.refName != "apply" => Some(methodApply)
        case Some(ScalaResolveResult(fun: ScFunctionDefinition, _)) if isAncestor(fun) => Some(RECURSION)
        case _ => None
      }
    }

    def forType(te: ScTypeElement): Option[UsageType] = {
      te.getParent match {
        case f: ScFunction if f.returnTypeElement.contains(te) => Some(CLASS_METHOD_RETURN_TYPE)
        case v: ScValue if v.typeElement.contains(te) => Some(if (v.isLocal) CLASS_LOCAL_VAR_DECLARATION else CLASS_FIELD_DECLARATION)
        case v: ScVariable if v.typeElement.contains(te) => Some(if (v.isLocal) CLASS_LOCAL_VAR_DECLARATION else CLASS_FIELD_DECLARATION)
        case cp: ScClassParameter if cp.isEffectiveVal && cp.typeElement.contains(te) => Some(CLASS_FIELD_DECLARATION)
        case ts: ScTypedStmt if ts.typeElement.contains(te) => Some(typedStatement)
        case _: ScSelfTypeElement => Some(selfType)
        case _: ScTypeAliasDeclaration | _: ScTypeParam => Some(typeBound)
        case _: ScTypeAliasDefinition => Some(typeAlias)
        case _ => None
      }
    }

    def forPattern(pattern: ScPattern): Option[UsageType] = {
      PsiTreeUtil.getParentOfType(pattern, classOf[ScCatchBlock]) match {
        case null =>
        case ScCatchBlock(clauses) if clauses.caseClauses.flatMap(_.pattern).exists(isAncestor) => return Some(CLASS_CATCH_CLAUSE_PARAMETER_DECLARATION)
        case _ =>
      }
      pattern match {
        case ScTypedPattern(te) if isAncestor(te) => Some(classTypedPattern)
        case _: ScConstructorPattern | _: ScInfixPattern => Some(extractor)
        case _ => None
      }
    }

    def usageType(parent: PsiElement): Option[UsageType] = {
      parent match {
        case _: ScImportExpr => Some(CLASS_IMPORT)
        case ta: ScTypeArgs => Some(typeArgsUsageType(ta))
        case tp: ScTemplateParents => templateParentsUsageType(tp)
        case param: ScParameter if isAncestor(param) => Some(CLASS_METHOD_PARAMETER_DECLARATION)
        case p: ScPattern => forPattern(p)
        case te: ScTypeElement => forType(te)
        case _: ScInterpolatedStringPartReference => Some(prefixInterpolatedString)
        case ref: ScReferenceExpression => refExprUsage(ref)
        case a: ScAnnotationExpr if isSomeAncestor(a.constr.reference) => Some(ANNOTATION)
        case t: ScThisReference if isSomeAncestor(t.reference) => Some(thisReference)
        case s: ScSuperReference if isSomeAncestor(s.reference) => Some(DELEGATE_TO_SUPER)
        case _: ScAccessModifier => Some(accessModifier)
        case p: ScPackaging if isSomeAncestor(p.reference) => Some(packageClause)
        case assign: ScAssignStmt if isAncestor(assign.getLExpression) =>
          if (assign.isNamedParameter) Some(namedParameter)
          else Some(WRITE)
        case MethodValue(_) => Some(functionExpression)
        case _: ScBlock | _: ScTemplateBody | _: ScEarlyDefinitions => Some(READ)
        case _ => None
      }
    }

    if (element.containingScalaFile.isDefined) {

      /** Classify an element found by [[org.jetbrains.plugins.scala.findUsages.parameters.ConstructorParamsInConstructorPatternSearcher]] */
      (element, targets) match {
        case (ref: ScReferenceElement, Array(only: PsiElementUsageTarget)) =>
          def isConstructorPatternParam(elem: PsiElement) = elem match {
            case bp: ScBindingPattern if PsiTreeUtil.getParentOfType(bp, classOf[ScConstructorPattern], classOf[ScInfixPattern]) != null => true
            case _ => false
          }

          if (isConstructorPatternParam(ref.resolve()) && !ref.isReferenceTo(only.getElement)) {
            return parameterInPattern
          }
        case _ =>
      }

      val result = element.withParentsInFile.flatMap(usageType).headOption

      result.orNull

      // TODO more of these, including Scala specific: case class/object, pattern match, type ascription, ...

    } else null
  }
}

object ScalaUsageTypeProvider {
  private implicit def stringToUsageType(name: String): UsageType = new UsageType(name)

  val extractor: UsageType = "Extractor"
  val classTypedPattern: UsageType = "Typed Pattern"
  val typedStatement: UsageType = "Typed Statement"
  val methodApply: UsageType = "Method `apply`"
  val thisReference: UsageType = "This Reference"
  val accessModifier: UsageType = "Access Modifier"
  val packageClause: UsageType = "Package Clause"
  val functionExpression: UsageType = "Function expression"
  val namedParameter: UsageType = "Named parameter"
  val prefixInterpolatedString: UsageType = "Interpolated string prefix"
  val parameterInPattern: UsageType = "Parameter in pattern"
  val selfType: UsageType = "Self type"
  val typeBound: UsageType = "Type bound"
  val typeAlias: UsageType = "Type alias"
}