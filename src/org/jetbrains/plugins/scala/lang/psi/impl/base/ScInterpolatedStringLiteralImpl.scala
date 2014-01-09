package org.jetbrains.plugins.scala
package lang.psi.impl.base

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.{InterpolatedStringType, ScInterpolatedStringLiteral}
import lang.psi.types.ScType
import lang.psi.types.result.{Failure, TypeResult, TypingContext}
import lang.psi.api.expr.{ScBlockExpr, ScReferenceExpression, ScExpression}
import lang.psi.impl.expr.ScInterpolatedStringPrefixReference
import lang.psi.impl.ScalaPsiElementFactory
import caches.CachesUtil
import com.intellij.psi.util.PsiModificationTracker
import lang.lexer.ScalaTokenTypes
import lang.psi.ScalaPsiUtil
import scala.collection.mutable.ListBuffer


/**
 * User: Dmitry Naydanov
 * Date: 3/17/12
 */

class ScInterpolatedStringLiteralImpl(node: ASTNode) extends ScLiteralImpl(node) with ScInterpolatedStringLiteral {
  def getType: InterpolatedStringType.StringType = node.getFirstChildNode.getText match {
    case "s" => InterpolatedStringType.STANDART
    case "f" => InterpolatedStringType.FORMAT
    case "id" => InterpolatedStringType.PATTERN
    case "raw" => InterpolatedStringType.RAW
    case _ => null
  }

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    getStringContextExpression match {
      case Some(expr) => expr.getNonValueType(ctx)
      case _ => Failure(s"Cannot find method ${getFirstChild.getText} of StringContext", Some(this))
    }
  }

  def reference: Option[ScReferenceExpression] = {
    getFirstChild match {
      case ref: ScReferenceExpression => Some(ref)
      case _ => None
    }
  }

  def getInjections: Array[ScExpression] = {
    getNode.getChildren(null).flatMap { _.getPsi match {
        case a: ScBlockExpr => Array[ScExpression](a)
        case _: ScInterpolatedStringPrefixReference => Array[ScExpression]()
        case b: ScReferenceExpression => Array[ScExpression](b)
        case _ => Array[ScExpression]()
      }
    }
  }

  def getStringParts(l: ScInterpolatedStringLiteral): Seq[String] = {
    val childNodes = l.children.map(_.getNode)
    val result = ListBuffer[String]()
    val emptyString = ""
    for {
      child <- childNodes
    } {
      child.getElementType match {
        case ScalaTokenTypes.tINTERPOLATED_STRING =>
          child.getText.headOption match {
            case Some('"') => result += child.getText.substring(1)
            case Some(_) => result += child.getText
            case None => result += emptyString
          }
        case ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING =>
          child.getText.toCharArray match {
            case Array('"', '"', '"', _) => result += child.getText.substring(3)
            case Array(_) => result += child.getText
            case _ => result += emptyString
          }
        case ScalaTokenTypes.tINTERPOLATED_STRING_INJECTION | ScalaTokenTypes.tINTERPOLATED_STRING_END =>
          val prev = child.getTreePrev
          if (prev != null) prev.getElementType match {
            case ScalaTokenTypes.tINTERPOLATED_MULTILINE_STRING | ScalaTokenTypes.tINTERPOLATED_STRING =>
            case _ => result += emptyString //insert empty string between injections
          }
        case _ =>
      }
    }
    result.toSeq
  }

  def getStringContextExpression: Option[ScExpression] = {
    def getExpandedExprBuilder(l: ScInterpolatedStringLiteral) = {
      val parts = getStringParts(l).mkString("\"", "\", \"", "\"") //making list of string literals
      val params = l.getInjections.map(_.getText).mkString("(", ",", ")")
      Option(ScalaPsiElementFactory.createExpressionWithContextFromText(s"StringContext($parts).${getFirstChild.getText}$params",
        node.getPsi.getContext, node.getPsi))
    }

    CachesUtil.get(this, CachesUtil.STRING_CONTEXT_EXPANDED_EXPR_KEY,
      new CachesUtil.MyProvider[ScInterpolatedStringLiteral, Option[ScExpression]](this, getExpandedExprBuilder)(PsiModificationTracker.MODIFICATION_COUNT))
  }
  
  override def isMultiLineString: Boolean = getText.endsWith("\"\"\"")

  override def isString: Boolean = true

  override def getValue: AnyRef = findChildByClassScala(classOf[ScLiteralImpl]) match {
    case literal: ScLiteralImpl => literal.getValue
    case _ => "" 
  }
}
