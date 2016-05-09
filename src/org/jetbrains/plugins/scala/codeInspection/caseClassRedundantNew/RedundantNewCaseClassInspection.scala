package org.jetbrains.plugins.scala.codeInspection.caseClassRedundantNew

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.AbstractInspection
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScClassParents
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
  * mattfowler
  * 5/7/2016
  */
class RedundantNewCaseClassInspection extends AbstractInspection("RedundantNewCaseClass", "Redundant New on Case Class") {

  override def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case newTemplate: ScNewTemplateDefinition =>
      if (hasRedundantNew(newTemplate)) {
        holder.registerProblem(newTemplate.getFirstChild, ScalaBundle.message("new.on.case.class.instantiation.redundant"),
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new RemoveNewQuickFix(newTemplate))
      }
  }

  private def hasRedundantNew(newTemplate: ScNewTemplateDefinition): Boolean = {
    val constructor = getConstructorFromTemplate(newTemplate)
    val resolvedConstructor = resolveConstructor(constructor)

    isCreatingSameType(newTemplate) && constructorCallHasArgumentList(constructor) &&
      isProblemlessPrimaryConstructorOfCaseClass(resolvedConstructor) && !isTypeAlias(resolvedConstructor)
  }

  /**
    * Determines if the type of the extends block is the same as the type of the new template type.
    * This prevents us from incorrectly displaying a warning when creating anonymous classes or instances with
    * mixin traits.
    */
  private def isCreatingSameType(newTemplate: ScNewTemplateDefinition): Boolean = {
    newTemplate.extendsBlock.templateParents.exists(_.typeElementsWithoutConstructor.isEmpty)
  }

  private def isTypeAlias(maybeResolveResult: Option[ScalaResolveResult]): Boolean = {
    maybeResolveResult.map(_.getActualElement).exists {
      case _: ScTypeAlias => true
      case _ => false
    }
  }

  private def getConstructorFromTemplate(newTemplate: ScNewTemplateDefinition): Option[ScConstructor] = {
    newTemplate.extendsBlock.firstChild.flatMap {
      case parents: ScClassParents => parents.constructor
      case _ => None
    }
  }

  private def constructorCallHasArgumentList(maybeConstructor: Option[ScConstructor]): Boolean = {
    maybeConstructor.flatMap(_.args).isDefined
  }

  private def resolveConstructor(maybeConstructor: Option[ScConstructor]): Option[ScalaResolveResult] = {
    for {
      constructor <- maybeConstructor
      ref <- constructor.reference
      resolved <- ref.advancedResolve
    } yield {
      resolved
    }
  }

  private def isProblemlessPrimaryConstructorOfCaseClass(maybeResolveResult: Option[ScalaResolveResult]): Boolean = {
    maybeResolveResult
      .filter(_.problems.isEmpty)
      .map(_.element)
      .exists {
        case ScPrimaryConstructor.ofClass(clazz) => clazz.isCase
        case _ => false
      }
  }
}