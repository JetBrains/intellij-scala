package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import implicits.ScImplicitlyConvertible
import api.statements.ScFunction
import com.intellij.psi._
import com.intellij.psi.scope._
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import com.intellij.openapi.progress.ProgressManager
import api.toplevel.imports.usages.ImportUsed
import lang.resolve.{MethodResolveProcessor, CompletionProcessor, ScalaResolveResult}
import types.{Compatibility, ScParameterizedType, ScFunctionType, ScType}
import types.result.{Success, Failure, TypeResult, TypingContext}

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScForStatementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScForStatement {

  override def toString: String = "ForStatement"

  def isYield: Boolean = findChildByType(ScalaTokenTypes.kYIELD) != null

  def enumerators: Option[ScEnumerators] = findChild(classOf[ScEnumerators])

  // Binding patterns in reverse order
  def patterns: Seq[ScPattern] = enumerators match {
    case None => return Seq.empty
    case Some(x) => return x.namings.reverse.map((n: Patterned) => n.pattern)
  }

  type Patterned = {
    def pattern: ScPattern
  }

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    val enumerators: ScEnumerators = this.enumerators match {
      case None => return true
      case Some(x) => x
    }
    if (lastParent == enumerators) return true
    enumerators.processDeclarations(processor, state, null, place)
  }


  override protected def innerType(ctx: TypingContext): TypeResult[ScType] = {
    val exprText = new StringBuilder
    val (enums, gens) = enumerators match {
      case None => return Failure("No enumerators", Some(this))
      case Some(x) => {
        (x.enumerators, x.generators)
      }
    }
    if (enums.length == 0 && gens.length == 1) {
      val gen = gens(0)
      exprText.append("(").append(gen.rvalue.getText).append(")").append(".").append(if (isYield) "map" else "foreach")
              .append(" { case ").
        append(gen.pattern.getText).append( "=> ")
      body match {
        case Some(x) => exprText.append(x.getText)
        case _ =>
      }
      exprText.append(" } ")
    } else {
      //todo:
    }
    try {
      val newExpr = ScalaPsiElementFactory.createExpressionWithContextFromText(exprText.toString, this.getContext)
      return newExpr.getNonValueType(ctx)
    }
    catch {
      case e: Exception => return Failure("Cannot create expression", Some(this))
    }
  }
}