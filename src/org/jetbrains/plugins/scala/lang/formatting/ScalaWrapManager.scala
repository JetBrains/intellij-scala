package org.jetbrains.plugins.scala.lang.formatting

import settings.ScalaCodeStyleSettings
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr
import com.intellij.formatting.{WrapType, Wrap}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScInfixPattern
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScInfixTypeElement

/**
 * @author Alexander Podkhalyuzin
 */

object ScalaWrapManager {
  def suggestedWrap(block: ScalaBlock, settings: ScalaCodeStyleSettings): Wrap = {
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
          if (notSamePriority){
            return Wrap.createChildWrap(block.getWrap,
                                        WrapType.byLegacyRepresentation(settings.SCALA_BINARY_OPERATION_WRAP),
                                        false)
          }
          else return Wrap.createWrap(settings.SCALA_BINARY_OPERATION_WRAP, false)
        }
        case _ => return Wrap.createWrap(settings.SCALA_BINARY_OPERATION_WRAP, false)
      }
    }

    psi match {
      case psi: ScInfixExpr => {
        return wrapBinary(_.isInstanceOf[ScInfixExpr], _.asInstanceOf[ScInfixExpr].operation, true)
      }
      case psi: ScInfixPattern => {
        return wrapBinary(_.isInstanceOf[ScInfixPattern], _.asInstanceOf[ScInfixPattern].refernece, false)
      }
      case psi: ScInfixTypeElement => {
        return wrapBinary(_.isInstanceOf[ScInfixTypeElement], _.asInstanceOf[ScInfixTypeElement].ref, false)
      }
      case _ =>
    }
    return null
  }

  def arrangeSuggestedWrapForChild(parent: ScalaBlock, child: ASTNode, settings: ScalaCodeStyleSettings,
                                   suggestedWrap: Wrap): Wrap = {
    val parentNode = parent.getNode
    val parentPsi = parentNode.getPsi
    val childPsi = child.getPsi

    def arrageBinary(elementMatch: PsiElement => Boolean,
                     elementOperation: PsiElement => PsiElement,
                     elementRightSide: PsiElement => PsiElement): Wrap = {
      childPsi.getParent match {
        case parent: PsiElement if elementMatch(parent) => {
          if (elementOperation(parent) == childPsi) return null
          if (parent != parentPsi) return suggestedWrap
          else if (elementRightSide(parentPsi) == childPsi) return suggestedWrap
          else return null
        }
        case _ => return null //hasn't to be
      }
    }

    parentPsi match {
      case inf: ScInfixExpr => {
        return arrageBinary(_.isInstanceOf[ScInfixExpr], _.asInstanceOf[ScInfixExpr].operation,
                            _.asInstanceOf[ScInfixExpr].rOp)
      }
      case inf: ScInfixPattern => {
        return arrageBinary(_.isInstanceOf[ScInfixPattern], _.asInstanceOf[ScInfixPattern].refernece,
                            _.asInstanceOf[ScInfixPattern].rightPattern.getOrElse(null))
      }
      case inf: ScInfixTypeElement => {
        return arrageBinary(_.isInstanceOf[ScInfixTypeElement], _.asInstanceOf[ScInfixTypeElement].ref,
                            _.asInstanceOf[ScInfixTypeElement].rOp.getOrElse(null))
      }
      case _ =>
    }
    return null
  }
}