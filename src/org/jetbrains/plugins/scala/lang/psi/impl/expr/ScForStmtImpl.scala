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
    val gen = enumerators match {
      case None => return Failure("No enumerators", Some(this))
      case Some(enum) =>
        if (enum.generators.length == 0) return Failure("No Generators in For Statement", Some(this))
        else {
          enum.generators.apply(0)
        }
    }
    val isYield = gen.getParent.getParent.asInstanceOf[ScForStatement].isYield
    var next = gen.getNextSibling
    if (gen.rvalue == null) return null
    //var tp = gen.rvalue.getType(TypingContext.empty).getOrElse(return None) //todo: now it's not used
    while (next != null && !next.isInstanceOf[ScGenerator]) {
      next match {
        case g: ScGuard => //todo: replace type tp to appropriate after this operation
        case e: ScEnumerator => //todo:
        case _ =>
      }
      next = next.getNextSibling
    }
    val nextGen = next != null
    val refName = {
      (nextGen, isYield) match {
        case (true, true) => "flatMap"
        case (true, false) => "foreach"
        case (false, true) => "map"
        case (false, false) => "foreach"
      }
    }
    import Compatibility.Expression
    val processor = new MethodResolveProcessor(gen.rvalue, refName, List(Seq(new Expression(psi.types.Nothing))) /*todo*/ , Seq.empty /*todo*/ , None)
    def processTypes(e: ScExpression) {
      ProgressManager.checkCanceled
      val result = e.getType(TypingContext.empty).getOrElse(return) //do not resolve if Type is unknown
      processor.processType(result, e, ResolveState.initial)
      if (processor.candidates.length == 0 || (processor.isInstanceOf[CompletionProcessor] &&
              processor.asInstanceOf[CompletionProcessor].collectImplicits)) {
        for (t <- e.getImplicitTypes) {
          ProgressManager.checkCanceled
          val importsUsed = e.getImportsForImplicit(t)
          import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
          var state = ResolveState.initial.put(ImportUsed.key, importsUsed)
          e.getClazzForType(t) match {
            case Some(cl: PsiClass) => state = state.put(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY, cl)
            case _ =>
          }
          processor.processType(t, e, state)
        }
      }
    }
    processTypes(gen.rvalue)
    //todo: duplicate from ScPattern.expectedType
    if (processor.candidates.length != 1) return Failure("Cannot resolve method " + refName, Some(this))
    else {
      val res = processor.candidates.apply(0)
      res match {
        case ScalaResolveResult(method: ScFunction, subst) => {
          method.getType(TypingContext.empty) match {
            case Success(ScFunctionType(f@ScFunctionType(rt, _), _), _) if f.isImplicit =>
              return Success(subst.subst(rt), Some(this))
            case Success(ScFunctionType(rt, _), _) => return Success(subst.subst(rt), Some(this))
            case f: Failure => return f
            case _ => return Failure("Cannot resolve method " + refName, Some(this))
          }
        }
        case _ => return Failure("Cannot resolve method " + refName, Some(this))
      }
    }
  }
}