package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.formatting.{Wrap, WrapType}
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCompositePattern, ScInfixPattern, ScPattern, ScPatternArgumentList}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScInfixTypeElement, ScSequenceArg}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
 * @author Alexander Podkhalyuzin
 */

object ScalaWrapManager {
  def suggestedWrap(block: ScalaBlock, scalaSettings: ScalaCodeStyleSettings): Wrap = {
    val settings = block.getCommonSettings
    val node = block.getNode
    val psi = node.getPsi
    def wrapBinary(elementMatch: PsiElement => Boolean,
                   elementOperation: PsiElement => PsiElement,
                   assignments: Boolean): Wrap = {
      psi.getParent match {
        case parent: PsiElement if elementMatch(parent) => {
          import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils.priority
          val parentPriority = priority(elementOperation(parent).getText, assignments)
          val childPriority = priority(elementOperation(psi).getText, assignments)
          val notSamePriority = parentPriority != childPriority
          if (notSamePriority) {
            Wrap.createChildWrap(block.getWrap,
                                        WrapType.byLegacyRepresentation(settings.BINARY_OPERATION_WRAP),
                                        false)
          }
          else Wrap.createWrap(settings.BINARY_OPERATION_WRAP, false)
        }
        case _ => Wrap.createWrap(settings.BINARY_OPERATION_WRAP, false)
      }
    }

    psi match {
      case psi: ScInfixExpr => {
        return wrapBinary(_.isInstanceOf[ScInfixExpr], _.asInstanceOf[ScInfixExpr].operation, assignments = true)
      }
      case psi: ScInfixPattern => {
        return wrapBinary(_.isInstanceOf[ScInfixPattern], _.asInstanceOf[ScInfixPattern].refernece, assignments = false)
      }
      case psi: ScInfixTypeElement => {
        return wrapBinary(_.isInstanceOf[ScInfixTypeElement], _.asInstanceOf[ScInfixTypeElement].ref, assignments = false)
      }
      case psi: ScCompositePattern => {
        return Wrap.createWrap(settings.BINARY_OPERATION_WRAP, false)
      }
      case psi: ScArgumentExprList => {
        val parentSuggestedWrap = block.myParentBlock.suggestedWrap
        val wrap = if (parentSuggestedWrap != null) Wrap.createChildWrap(parentSuggestedWrap,
                                        WrapType.byLegacyRepresentation(settings.CALL_PARAMETERS_WRAP), false)
        else Wrap.createWrap(settings.CALL_PARAMETERS_WRAP, false)
        if (settings.PREFER_PARAMETERS_WRAP) {
          wrap.ignoreParentWraps
        }
        return wrap
      }
      case psi: ScReferenceExpression => {
        return Wrap.createWrap(settings.METHOD_CALL_CHAIN_WRAP, true)
      }
      case psi: ScMethodCall => {
        return Wrap.createWrap(settings.METHOD_CALL_CHAIN_WRAP, true)
      }
      case psi: ScPatternArgumentList => {
        return Wrap.createWrap(settings.CALL_PARAMETERS_WRAP, false)
      }
      case _ if node.getElementType == ScalaTokenTypes.kEXTENDS && block.myLastNode != null => {
        return Wrap.createChildWrap(block.getWrap, WrapType.byLegacyRepresentation(settings.EXTENDS_LIST_WRAP), true)
      }
      case psi: ScParameterClause => {
        return Wrap.createWrap(settings.METHOD_PARAMETERS_WRAP, false)
      }
      case psi: ScParameters => {
        return Wrap.createWrap(settings.METHOD_PARAMETERS_WRAP, true)
      }
      case annot: ScAnnotations if annot.getAnnotations.length > 0 => {
        annot.getParent match {
          case _: ScTypeDefinition => return Wrap.createWrap(settings.CLASS_ANNOTATION_WRAP, false)
          case _: ScFunction => return Wrap.createWrap(settings.METHOD_ANNOTATION_WRAP, false)
          case _: ScVariable | _: ScValue | _: ScTypeAlias if {
            annot.getParent.getParent match { case _: ScEarlyDefinitions | _: ScTemplateBody => true; case _ => false }
          } =>
            return Wrap.createWrap(settings.FIELD_ANNOTATION_WRAP, false)
          case _: ScVariable | _: ScValue | _: ScTypeAlias => Wrap.createWrap(settings.VARIABLE_ANNOTATION_WRAP, false)
          case _: ScParameter => Wrap.createWrap(settings.PARAMETER_ANNOTATION_WRAP, false)
          case _ =>
        }
      }
      case _ =>
    }
    null
  }

  def arrangeSuggestedWrapForChild(parent: ScalaBlock, child: ASTNode, scalaSettings: ScalaCodeStyleSettings,
                                   suggestedWrap: Wrap): Wrap = {
    val settings = parent.getCommonSettings
    val parentNode = parent.getNode
    val parentPsi = parentNode.getPsi
    val childPsi = child.getPsi
    if (childPsi.isInstanceOf[ScExtendsBlock] &&
            childPsi.getFirstChild != null && !childPsi.getFirstChild.isInstanceOf[ScTemplateBody])
      return Wrap.createWrap(settings.EXTENDS_KEYWORD_WRAP, true)

    def arrageBinary(elementMatch: PsiElement => Boolean,
                     elementOperation: PsiElement => PsiElement,
                     elementRightSide: PsiElement => PsiElement,
                     elementLeftSide: PsiElement => PsiElement): Wrap = {
      childPsi.getParent match {
        case parent: PsiElement if elementMatch(parent) => {
          if (elementOperation(parent) == childPsi) return null
          if (parent != parentPsi) suggestedWrap
          else if (elementLeftSide(parentPsi) == childPsi) suggestedWrap
          else if (elementRightSide(parentPsi) == childPsi) suggestedWrap
          else null
        }
        case _ => null //hasn't to be
      }
    }

    parentPsi match {
      case inf: ScInfixExpr => {
        return arrageBinary(_.isInstanceOf[ScInfixExpr], _.asInstanceOf[ScInfixExpr].operation,
                            _.asInstanceOf[ScInfixExpr].rOp, _.asInstanceOf[ScInfixExpr].lOp)
      }
      case inf: ScInfixPattern => {
        return arrageBinary(_.isInstanceOf[ScInfixPattern], _.asInstanceOf[ScInfixPattern].refernece,
                            _.asInstanceOf[ScInfixPattern].rightPattern.orNull,
                            _.asInstanceOf[ScInfixPattern].leftPattern)
      }
      case inf: ScInfixTypeElement => {
        return arrageBinary(_.isInstanceOf[ScInfixTypeElement], _.asInstanceOf[ScInfixTypeElement].ref,
                            _.asInstanceOf[ScInfixTypeElement].rOp.orNull,
                            _.asInstanceOf[ScInfixTypeElement].lOp)
      }
      case psi: ScCompositePattern => {
        if (childPsi.isInstanceOf[ScPattern]) return suggestedWrap
        else return null
      }
      case call: ScMethodCall => {
        if (child.getElementType == ScalaTokenTypes.tDOT) return suggestedWrap
        else return null
      }
      case ref: ScReferenceExpression => {
        if (child.getElementType == ScalaTokenTypes.tDOT) return suggestedWrap
        else return null
      }
      case args: ScArgumentExprList => {
        if (childPsi.isInstanceOf[ScExpression]) return suggestedWrap
        else return null
      }
      case patt: ScPatternArgumentList => {
        childPsi match {
          case _: ScPattern => return suggestedWrap
          case _: ScSequenceArg => return suggestedWrap
          case _ => return null
        }
      }
      case params: ScParameterClause => {
        if (childPsi.isInstanceOf[ScParameter]) return suggestedWrap
        else return null
      }
      case params: ScParameters => {
        if (childPsi.isInstanceOf[ScParameterClause] && params.clauses.apply(0) != childPsi) return suggestedWrap
        else return null
      }
      case annot: ScAnnotations => {
        if (childPsi.isInstanceOf[ScAnnotation]) return suggestedWrap
        else return null
      }
      case _ if parentNode.getElementType == ScalaTokenTypes.kEXTENDS && parent.myLastNode != null => {
        val e: ScExtendsBlock = PsiTreeUtil.getParentOfType(parentPsi, classOf[ScExtendsBlock])
        val first: PsiElement = e.earlyDefinitions match {
          case Some(z) => z
          case _ => e.templateParents match {
            case Some(tp) if tp.typeElements.length > 0 => tp.typeElements(0)
            case _ => null
          }
        }
        if (first == null) return null
        if (childPsi == first) return suggestedWrap
        if (scalaSettings.WRAP_BEFORE_WITH_KEYWORD) {
          if (child.getElementType == ScalaTokenTypes.kWITH) return suggestedWrap
          else return null
        } else {
          e.templateParents match {
            case Some(tp) if tp.typeElements.exists(_ == childPsi) => return suggestedWrap
            case _ => return null
          }
        }
      }
      case _ =>
    }
    null
  }
}