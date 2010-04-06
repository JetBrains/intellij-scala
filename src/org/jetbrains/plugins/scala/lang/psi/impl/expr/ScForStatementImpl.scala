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
import lang.resolve.{ScalaResolveResult}
import types.result.{Success, Failure, TypeResult, TypingContext}
import types._
import collection.mutable.ArrayBuffer
import implicits.{ImplicitParametersCollector, ScImplicitlyConvertible}
import nonvalue.{Parameter, TypeParameter, ScMethodType, ScTypePolymorphicType}
import lang.resolve.processor.MethodResolveProcessor

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

    val failure = Failure("Cannot create expression", Some(this))
    val exprText = new StringBuilder
    val (enums, gens, guards) = enumerators match {
      case None => return Failure("No enumerators", Some(this))
      case Some(x) => {
        (x.enumerators, x.generators, x.guards)
      }
    }
    if (guards.length == 0 && enums.length == 0 && gens.length == 1) {
      val gen = gens(0)
      if (gen.rvalue == null)  return failure
      exprText.append("(").append(gen.rvalue.getText).append(")").append(".").append(if (isYield) "map" else "foreach")
              .append(" { case ").
      append(gen.pattern.getText).append( "=> ")
      body match {
        case Some(x) => exprText.append(x.getText)
        case _ => exprText.append("{}")
      }
      exprText.append(" } ")
    } else if (gens.length > 0){
      val gen = gens(0)
      if (gen.rvalue == null) return failure
      var next = gens(0).getNextSibling
      while (next != null && !next.isInstanceOf[ScGuard] && !next.isInstanceOf[ScEnumerator] &&
              !next.isInstanceOf[ScGenerator]) next = next.getNextSibling
      next match {
        case null =>
        case guard: ScGuard => {
          exprText.append("for {").append(gen.pattern.getText).
                  append(" <- ((").append(gen.rvalue.getText).append(").filter(").
                  append(gen.pattern.bindings.map(b => b.name).mkString("(",", ", ")")).append("))")
          next = next.getNextSibling
          while (next != null && !next.isInstanceOf[ScGuard] && !next.isInstanceOf[ScEnumerator] &&
              !next.isInstanceOf[ScGenerator]) next = next.getNextSibling
          if (next != null) exprText.append(";")
          while (next != null) {
            exprText.append(next.getText)
            next = next.getNextSibling
          }
          exprText.append("\n} ")
          if (isYield) exprText.append("yield ")
          body match {
            case Some(x) => exprText append x.getText
            case _ => exprText append "{}"
          }
        }
        case gen2: ScGenerator => {
          exprText.append("(").append(gen.rvalue.getText).append(")").append(".").append(if (isYield) "flatMap " else "foreach ").append("{case ").
            append(gen.pattern.getText).append(" => ").append("for {")
          while (next != null) {
            exprText.append(next.getText)
            next = next.getNextSibling
          }
          exprText.append("\n} ")
          if (isYield) exprText.append("yield ")
          body match {
            case Some(x) => exprText append x.getText
            case _ => exprText append "{}"
          }
          exprText.append("\n}")
        }
        case enum: ScEnumerator => {
          if (enum.rvalue == null) return failure
          exprText.append("for {(").append(enum.pattern.getText).append(", ").append(gen.pattern.getText).
                  append(") <- (for (freshNameForIntelliJIDEA1@(").append(gen.pattern.getText).append(") <- ").
                  append(gen.rvalue.getText).append(") yield {val freshNameForIntelliJIDEA2@(").
                  append(enum.pattern.getText).append(") = ").append(enum.rvalue.getText).
                  append("; (freshNameForIntelliJIDEA2, freshNameForIntelliJIDEA1)})")
          next = next.getNextSibling
          while (next != null && !next.isInstanceOf[ScGuard] && !next.isInstanceOf[ScEnumerator] &&
              !next.isInstanceOf[ScGenerator]) next = next.getNextSibling
          if (next != null) exprText.append(";")
          while (next != null) {
            exprText.append(next.getText)
            next = next.getNextSibling
          }
          exprText.append("\n} ")
          if (isYield) exprText.append("yield ")
          body match {
            case Some(x) => exprText append x.getText
            case _ => exprText append "{}"
          }
        }
        case _ =>
      }
    }
    try {
      if (exprText.toString == "") return failure
      val newExpr = ScalaPsiElementFactory.createExpressionWithContextFromText(exprText.toString, this.getContext)
      return newExpr.getNonValueType(ctx)
    }
    catch {
      case e: Exception => return failure
    }
  }
}