package org.jetbrains.plugins.scala
package lang.psi.impl.base

import com.intellij.lang.ASTNode
import lang.psi.api.base.ScInterpolatedStringLiteral
import lang.psi.types.ScType
import lang.psi.types.result.{Failure, TypeResult, TypingContext}
import lang.psi.api.statements.ScFunctionDefinition
import lang.psi.api.expr.{ScBlockExpr, ScReferenceExpression, ScExpression}
import lang.psi.impl.expr.ScInterpolatedStringPrefixReference
import lang.psi.impl.ScalaPsiElementFactory
import caches.CachesUtil
import com.intellij.psi.util.PsiModificationTracker
import lang.lexer.ScalaTokenTypes
import lang.parser.ScalaElementTypes


/**
 * User: Dmitry Naydanov
 * Date: 3/17/12
 */

class ScInterpolatedStringLiteralImpl(node: ASTNode) extends ScLiteralImpl(node) with ScInterpolatedStringLiteral {
  def getType: InterpolatedStringType.StringType = node.getFirstChildNode.getText match {
    case "s" => InterpolatedStringType.STANDART
    case "f" => InterpolatedStringType.FORMAT
    case "id" => InterpolatedStringType.PATTERN
    case _ => null
  }

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] =
    Option(getFirstChild.findReferenceAt(0)).map(_.resolve()).getOrElse(null) match {
      case f: ScFunctionDefinition => f.returnType
      case _ => Failure(s"Cannot find method ${getFirstChild.getText} of String Context", Some(this))
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

  def getStringContextExpression: Option[ScExpression] = {
    def getExpandedExprBuilder(l: ScInterpolatedStringLiteral) = {
      val params = l.getInjections.map(_.getText).mkString("(", ",", ")")
      Option(ScalaPsiElementFactory.createExpressionWithContextFromText(s"StringContext(str).${getFirstChild.getText}$params",
        node.getPsi.getContext, node.getPsi))
    }

    CachesUtil.get(this, CachesUtil.STRING_CONTEXT_EXPANDED_EXPR_KEY,
      new CachesUtil.MyProvider[ScInterpolatedStringLiteral, Option[ScExpression]](this, getExpandedExprBuilder)(PsiModificationTracker.MODIFICATION_COUNT))
  }
  
  override def isMultiLineString: Boolean = getText.endsWith("\"\"\"")

  override def isString: Boolean = this.getLastChild.getNode.getElementType match {
    case ScalaTokenTypes.tINTERPOLATED_STRING_END => true
    case _ => false
  }
}
