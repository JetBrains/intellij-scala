package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope._
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.resolve.StdKinds
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor

import scala.annotation.tailrec
import scala.collection.mutable

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScForStatementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScForStatement {
  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "ForStatement"

  def isYield: Boolean = findChildByType(ScalaTokenTypes.kYIELD) != null

  def enumerators: Option[ScEnumerators] = findChild(classOf[ScEnumerators])

  // Binding patterns in reverse order
  def patterns: Seq[ScPattern] = enumerators match {
    case None => Seq.empty
    case Some(x) => x.namings.reverse.map(_.pattern)
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
  
  protected def bodyToText(expr: ScExpression) = expr.getText

  @tailrec
  private def nextEnumerator(gen: PsiElement): PsiElement = {
    gen.getNextSibling match {
      case guard: ScGuard => guard
      case enum: ScEnumerator => enum
      case gen: ScGenerator => gen
      case null => null
      case elem => nextEnumerator(elem)
    }
  }

  def getDesugarizedExprText(forDisplay: Boolean): Option[String] = {
    val exprText: StringBuilder = new StringBuilder
    val arrow = ScalaPsiUtil.functionArrow(getProject)
    val (enums, gens, guards) = enumerators match {
      case None => return None
      case Some(x) => (x.enumerators, x.generators, x.guards)
    }
    if (guards.length == 0 && enums.length == 0 && gens.length == 1) {
      val gen = gens(0)
      if (gen.rvalue == null) return None
      exprText.append("(").append(gen.rvalue.getText).append(")").append(".").append(if (isYield) "map" else "foreach")
              .append(" { case ")
      gen.pattern.desugarizedPatternIndex = exprText.length
      exprText.append(gen.pattern.getText).append(s" $arrow ")
      body match {
        case Some(x) => exprText.append(bodyToText(x))
        case _ => exprText.append("{}")
      }
      exprText.append(" } ")
    } else if (gens.length > 0) {
      val gen = gens(0)
      if (gen.rvalue == null) return None
      var next = nextEnumerator(gen)
      next match {
        case null =>
        case guard: ScGuard =>
          exprText.append("for {")
          gen.pattern.desugarizedPatternIndex = exprText.length
          var filterText = "withFilter"
          var filterFound = false
          val tp = gen.rvalue.getType(TypingContext.empty).getOrAny
          val processor =
            new CompletionProcessor(StdKinds.methodRef, this, collectImplicits = true, forName = Some("withFilter")) {
              override def execute(_element: PsiElement, state: ResolveState): Boolean = {
                super.execute(_element, state)
                if (!levelSet.isEmpty) {
                  filterFound = true
                  false
                } else true
              }
            }
          processor.processType(tp, this)
          if (!filterFound) filterText = "filter"
          exprText.append(gen.pattern.getText).
                  append(" <- ((").append(gen.rvalue.getText).append(s").$filterText { case ").
                  append(gen.pattern.bindings.map(b => b.name).mkString("(", ", ", ")")).append(s" $arrow ")
                  if (forDisplay) {
                    exprText.append(guard.expr.map(_.getText).getOrElse("true"))
                  } else {
                    exprText.append(guard.expr.map(_.getText).getOrElse("true")).append(";true")
                  }
          exprText.append("})")

          next = nextEnumerator(next)
          if (next != null) exprText.append(" ; ")
          while (next != null) {
            next match {
              case gen: ScGenerator =>
                gen.pattern.desugarizedPatternIndex = exprText.length
              case _ =>
            }
            exprText.append(next.getText)
            next = next.getNextSibling
          }
          exprText.append("\n} ")
          if (isYield) exprText.append("yield ")
          body match {
            case Some(x) => exprText append bodyToText(x)
            case _ => exprText append "{}"
          }
        case gen2: ScGenerator =>
          exprText.append("(").append(gen.rvalue.getText).append(")").append(".").
                  append(if (isYield) "flatMap " else "foreach ").append("{ case ")
          gen.pattern.desugarizedPatternIndex = exprText.length
          exprText.append(gen.pattern.getText).append(s" $arrow ").append("for {")
          while (next != null) {
            next match {
              case gen: ScGenerator =>
                gen.pattern.desugarizedPatternIndex = exprText.length
              case _ =>
            }
            exprText.append(next.getText)
            next = next.getNextSibling
          }
          exprText.append("\n} ")
          if (isYield) exprText.append("yield ")
          body match {
            case Some(x) => exprText append bodyToText(x)
            case _ => exprText append "{}"
          }
          exprText.append("\n}")
        case enum: ScEnumerator =>
          if (enum.rvalue == null) return None
          exprText.append("for {(").append(enum.pattern.getText).append(", ")
          gen.pattern.desugarizedPatternIndex = exprText.length
          exprText.append(gen.pattern.getText)

          val (freshName1, freshName2) = if (forDisplay) {
            ("x$1", "x$2")
          } else {
            ("freshNameForIntelliJIDEA1", "freshNameForIntelliJIDEA2")
          }

          exprText.append(") <- (for (").append(freshName1).append("@(").append(gen.pattern.getText).append(") <- ").
                  append(gen.rvalue.getText).append(") yield {val ").append(freshName2).append("@(").
                  append(enum.pattern.getText).append(") = ").append(enum.rvalue.getText).
                  append("; (").append(freshName2).append(", ").append(freshName1).append(")})")
          next = nextEnumerator(next)
          if (next != null) exprText.append(" ; ")
          while (next != null) {
            next match {
              case gen: ScGenerator =>
                gen.pattern.desugarizedPatternIndex = exprText.length
              case _ =>
            }
            exprText.append(next.getText)
            next = next.getNextSibling
          }
          exprText.append("\n} ")
          if (isYield) exprText.append("yield ")
          body match {
            case Some(x) => exprText append bodyToText(x)
            case _ => exprText append "{}"
          }
        case _ =>
      }
    }
    Some(exprText.toString())
  }

  def getDesugarizedExpr: Option[ScExpression] = {
    CachesUtil.get(this, CachesUtil.DESUGARIZED_EXPR_KEY,
      new CachesUtil.MyProvider[ScForStatementImpl, Option[ScExpression]](this, f => f.getDesugarizedExprImpl)
    (PsiModificationTracker.MODIFICATION_COUNT))
  }

  private def getDesugarizedExprImpl: Option[ScExpression] = {
    val res = getDesugarizedExprText(forDisplay = false) match {
      case Some(text) =>
        if (text == "") None
        else {
          try {
            Some(ScalaPsiElementFactory.createExpressionWithContextFromText(text, this.getContext, this))
          } catch {
            case e: Throwable => None
          }
        }
      case _ => None
    }

    val analogMap: mutable.HashMap[ScPattern, ScPattern] = mutable.HashMap.empty

    res match {
      case Some(expr: ScExpression) =>
        enumerators.map(e => e.generators.map(g => g.pattern)).foreach(patts =>
          patts.foreach(patt => {
            if (patt != null && patt.desugarizedPatternIndex != -1) {
              var element = expr.findElementAt(patt.desugarizedPatternIndex)
              while (element != null && (element.getTextLength < patt.getTextLength ||
                (!element.isInstanceOf[ScPattern] && element.getTextLength == patt.getTextLength)))
                element = element.getParent
              if (element != null && element.getText == patt.getText) {
                element match {
                  case p: ScPattern =>
                    analogMap.put(p, patt)
                    patt.analog = p
                  case _ =>
                }
              }
            }
          })
        )
      case _ =>
    }

    val (enums, gens, guards) = enumerators match {
      case None => return None
      case Some(x) => (x.enumerators, x.generators, x.guards)
    }

    def updateAnalog(f: ScForStatementImpl) {
      for {
        enums <- f.enumerators
        gen <- enums.generators
      } {
        analogMap.get(gen.pattern) match {
          case Some(oldElem) =>
            oldElem.analog = gen.pattern.analog
          case _ =>
        }
      }
    }
    if ((enums.isEmpty && guards.isEmpty && gens.length == 1) || gens.length == 0 || res.isEmpty) res
    else {
      val expr = res.get
      nextEnumerator(gens(0)) match {
        case null => res
        case guard: ScGuard =>
          //In this case we just need to replace for statement one more time
          expr match {
            case f: ScForStatementImpl =>
              val additionalReplacement = f.getDesugarizedExprImpl
              additionalReplacement match {
                case Some(repl) =>
                  updateAnalog(f)
                  Some(repl)
                case _ => res
              }
            case _ => res
          }
        case enum: ScEnumerator =>
          expr match {
            case f: ScForStatementImpl =>
              for {
                enums <- f.enumerators
                gen <- enums.generators.headOption
                ScParenthesisedExpr(f: ScForStatementImpl) = gen.rvalue
                additionalReplacement = f.getDesugarizedExprImpl
                repl <- additionalReplacement
              } {
                updateAnalog(f)
                f.replace(repl)
              }
              val additionalReplacement = f.getDesugarizedExprImpl
              updateAnalog(f)
              Some(additionalReplacement)
            case _ => res
          }
        case gen: ScGenerator =>
          expr match {
            case call: ScMethodCall =>
              for {
                expr <- call.args.exprs.headOption
                if expr.isInstanceOf[ScBlockExpr]
                bl = expr.asInstanceOf[ScBlockExpr]
                clauses <- bl.caseClauses
                clause <- clauses.caseClauses.headOption
                expr <- clause.expr
                if expr.isInstanceOf[ScForStatementImpl]
                f = expr.asInstanceOf[ScForStatementImpl]
                additionalReplacement = f.getDesugarizedExprImpl
                repl <- additionalReplacement
              } {
                updateAnalog(f)
                f.replace(repl)
              }
            case _ =>
          }
          res
      }
      res
    }
  }

  override protected def innerType(ctx: TypingContext): TypeResult[ScType] = {
    getDesugarizedExpr match {
      case Some(newExpr) => newExpr.getNonValueType(ctx)
      case None => Failure("Cannot create expression", Some(this))
    }
  }

  def getLeftParenthesis = {
    val leftParenthesis = findChildByType(ScalaTokenTypes.tLPARENTHESIS)
    if (leftParenthesis == null) None else Some(leftParenthesis)
  }

  def getRightParenthesis = {
    val rightParenthesis = findChildByType(ScalaTokenTypes.tRPARENTHESIS)
    if (rightParenthesis == null) None else Some(rightParenthesis)
  }

}