package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.formatting.{Wrap, WrapType}
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.{childOf, _}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCompositePattern, ScInfixPattern, ScPattern, ScPatternArgumentList}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScInfixTypeElement, ScSequenceArg}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScAnnotations}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}

object ScalaWrapManager {
  def suggestedWrap(block: ScalaBlock, scalaSettings: ScalaCodeStyleSettings): Wrap = {
    val settings = block.commonSettings
    val node = block.getNode
    val psi = node.getPsi

    def wrapBinary(elementMatch: PsiElement => Boolean,
                   elementOperation: PsiElement => PsiElement,
                   assignments: Boolean): Wrap = {
      psi.getParent match {
        case parent: PsiElement if elementMatch(parent) =>
          import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils.priority
          val parentPriority = priority(elementOperation(parent).getText, assignments)
          val childPriority = priority(elementOperation(psi).getText, assignments)
          val notSamePriority = parentPriority != childPriority
          if (notSamePriority) {
            Wrap.createChildWrap(block.getWrap, WrapType.byLegacyRepresentation(settings.BINARY_OPERATION_WRAP), false)
          } else {
            Wrap.createWrap(settings.BINARY_OPERATION_WRAP, false)
          }
        case _ =>
          Wrap.createWrap(settings.BINARY_OPERATION_WRAP, false)
      }
    }

    psi match {
      case _: ScInfixExpr =>
        wrapBinary(_.isInstanceOf[ScInfixExpr], _.asInstanceOf[ScInfixExpr].operation, assignments = true)
      case _: ScInfixPattern =>
        wrapBinary(_.isInstanceOf[ScInfixPattern], _.asInstanceOf[ScInfixPattern].operation, assignments = false)
      case _: ScInfixTypeElement =>
        wrapBinary(_.isInstanceOf[ScInfixTypeElement], _.asInstanceOf[ScInfixTypeElement].operation, assignments = false)
      case _: ScCompositePattern =>
        Wrap.createWrap(settings.BINARY_OPERATION_WRAP, false)
      case _: ScArgumentExprList =>
        val parentSuggestedWrap = block.parentBlock.suggestedWrap
        val wrap = if (parentSuggestedWrap != null) {
          Wrap.createChildWrap(parentSuggestedWrap, WrapType.byLegacyRepresentation(settings.CALL_PARAMETERS_WRAP), false)
        } else {
          Wrap.createWrap(settings.CALL_PARAMETERS_WRAP, false)
        }
        if (settings.PREFER_PARAMETERS_WRAP) {
          wrap.ignoreParentWraps()
        }
        wrap
      case _: ScReferenceExpression | _: ScThisReference | _: ScSuperReference =>
        Wrap.createWrap(settings.METHOD_CALL_CHAIN_WRAP, true)
      case _: ScMethodCall =>
        Wrap.createWrap(settings.METHOD_CALL_CHAIN_WRAP, settings.WRAP_FIRST_METHOD_IN_CALL_CHAIN)
      case _: ScPatternArgumentList =>
        Wrap.createWrap(settings.CALL_PARAMETERS_WRAP, false)
      case _ if node.getElementType == ScalaTokenTypes.kEXTENDS && block.lastNode != null =>
        Wrap.createChildWrap(block.getWrap, WrapType.byLegacyRepresentation(settings.EXTENDS_LIST_WRAP), true)
      case (_: ScParameterClause) childOf _ childOf(_: ScMember) =>
        Wrap.createWrap(settings.METHOD_PARAMETERS_WRAP, false)
      case (_: ScParameters) childOf (_: ScMember) =>
        Wrap.createWrap(settings.METHOD_PARAMETERS_WRAP, true)
      case annot: ScAnnotations if annot.getAnnotations.length > 0 =>
        annot.getParent match {
          case _: ScTypeDefinition => Wrap.createWrap(settings.CLASS_ANNOTATION_WRAP, false)
          case _: ScFunction => Wrap.createWrap(settings.METHOD_ANNOTATION_WRAP, false)
          case _: ScVariable | _: ScValue | _: ScTypeAlias if {
            annot.getParent.getParent match {
              case _: ScEarlyDefinitions | _: ScTemplateBody => true;
              case _ => false
            }
          } =>
            Wrap.createWrap(settings.FIELD_ANNOTATION_WRAP, false)
          case _: ScVariable | _: ScValue | _: ScTypeAlias =>
            Wrap.createWrap(settings.VARIABLE_ANNOTATION_WRAP, false)
          case _: ScParameter =>
            Wrap.createWrap(settings.PARAMETER_ANNOTATION_WRAP, false)
          case _ =>
            null
        }
      case _ =>
        null
    }
  }

  def arrangeSuggestedWrapForChild(parent: ScalaBlock, child: ASTNode, suggestedWrap: Wrap)
                                  (implicit scalaSettings: ScalaCodeStyleSettings): Wrap = {
    val settings = parent.commonSettings
    val parentNode = parent.getNode
    val parentPsi = parentNode.getPsi
    val childPsi = child.getPsi

    val childIsExtends = childPsi.isInstanceOf[ScExtendsBlock] &&
      childPsi.getFirstChild.nullSafe.map(_.elementType).contains(ScalaTokenTypes.kEXTENDS)
    if (childIsExtends)
      return Wrap.createWrap(settings.EXTENDS_KEYWORD_WRAP, true)

    def arrangeBinary(elementCollect: PartialFunction[PsiElement, (() => PsiElement,  PsiElement, () => PsiElement)]): Wrap = {
      elementCollect.lift(childPsi.getParent).map { case (left, operation, right) =>
        if (operation == childPsi) null
        else if (parent != parentPsi) suggestedWrap
        else if (left == childPsi) suggestedWrap
        else if (right == childPsi) suggestedWrap
        else null
      }.orNull
    }

    parentPsi match {
      case _: ScInfixExpr =>
        arrangeBinary { case e: ScInfixExpr => (() => e.left, e.operation, () => e.right) }
      case _: ScInfixPattern =>
        arrangeBinary { case p: ScInfixPattern => (() => p.left, p.operation, () => p.rightOption.orNull)}
      case _: ScInfixTypeElement =>
        arrangeBinary { case p: ScInfixTypeElement => (() => p.left, p.operation, () => p.rightOption.orNull)}
      case _: ScCompositePattern =>
        if (childPsi.isInstanceOf[ScPattern]) suggestedWrap
        else null
      case _: ScMethodCall =>
        if (child.getElementType == ScalaTokenTypes.tDOT) suggestedWrap
        else null
      case _: ScReferenceExpression | _: ScThisReference | _: ScSuperReference =>
        if (child.getElementType == ScalaTokenTypes.tDOT) suggestedWrap
        else null
      case _: ScArgumentExprList =>
        if (childPsi.isInstanceOf[ScExpression]) suggestedWrap
        else null
      case _: ScPatternArgumentList =>
        childPsi match {
          case _: ScPattern => suggestedWrap
          case _: ScSequenceArg => suggestedWrap
          case _ => null
        }
      case _: ScParameterClause =>
        if (childPsi.isInstanceOf[ScParameter]) suggestedWrap
        else null
      case params: ScParameters =>
        if (childPsi.isInstanceOf[ScParameterClause] && params.clauses.head != childPsi) suggestedWrap
        else null
      case _: ScAnnotations =>
        if (childPsi.isInstanceOf[ScAnnotation]) suggestedWrap
        else null
      case _ if parentNode.getElementType == ScalaTokenTypes.kEXTENDS && parent.lastNode != null =>
        val e: ScExtendsBlock = PsiTreeUtil.getParentOfType(parentPsi, classOf[ScExtendsBlock])
        val first: PsiElement = e.earlyDefinitions match {
          case Some(z) => z
          case _ => e.templateParents match {
            case Some(tp) if tp.typeElements.nonEmpty => tp.typeElements.head
            case _ => null
          }
        }
        if (first == null) null
        else if (childPsi == first) suggestedWrap
        else if (scalaSettings.WRAP_BEFORE_WITH_KEYWORD) {
          if (child.getElementType == ScalaTokenTypes.kWITH) suggestedWrap
          else null
        } else {
          e.templateParents match {
            case Some(tp) if tp.typeElements.contains(childPsi) => suggestedWrap
            case _ => null
          }
        }
      case _ =>
        null
    }
  }
}
