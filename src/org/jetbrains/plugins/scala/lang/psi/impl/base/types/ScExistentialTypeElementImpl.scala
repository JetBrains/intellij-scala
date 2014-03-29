package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import psi.ScalaPsiElementImpl
import api.base.types._
import api.statements.{ScTypeAliasDeclaration, ScValueDeclaration}
import com.intellij.lang.ASTNode

import _root_.scala.collection.mutable.ListBuffer
import psi.types._
import result.{TypeResult, Success, Failure, TypingContext}
import api.ScalaElementVisitor
import com.intellij.psi.{PsiElementVisitor, ResolveState, PsiElement}

/**
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

class ScExistentialTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScExistentialTypeElement {
  override def toString: String = "ExistentialType: " + getText

  protected def innerType(ctx: TypingContext) = {
    val q = quantified.getType(ctx)
    val problems: ListBuffer[TypeResult[ScType]] = new ListBuffer
    problems += q
    val wildcards: List[ScExistentialArgument] = {
      var buff: ListBuffer[ScExistentialArgument] = new ListBuffer
      for (decl <- clause.declarations) {
        decl match {
          case alias: ScTypeAliasDeclaration =>
            val lb = alias.lowerBound
            val ub = alias.upperBound
            problems += lb; problems += ub
            buff +=  new ScExistentialArgument(alias.name,
                                               alias.typeParameters.map{tp => ScalaPsiManager.typeVariable(tp)}.toList,
                                               lb.getOrNothing, ub.getOrAny)
          case value: ScValueDeclaration =>
            value.typeElement match {
              case Some(te) =>
                val ttype = te.getType(ctx)
                problems += ttype
                val t = ScCompoundType(Seq(ttype.getOrAny, Singleton), Map.empty, Map.empty)
                for (declared <- value.declaredElements) {
                  buff += ScExistentialArgument(declared.name, Nil, Nothing, t)
                }
              case None =>
            }
          case _ =>
        }
      }
      buff.toList
    }
    q flatMap { t =>
      val failures = for (f@Failure(_, _) <- problems) yield f
      failures.foldLeft(Success(ScExistentialType(t, wildcards), Some(this)))(_.apply(_))
    }
  }

  import com.intellij.psi.scope.PsiScopeProcessor

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    if (lastParent == quantified || (lastParent.isInstanceOf[ScalaPsiElement] &&
      lastParent.asInstanceOf[ScalaPsiElement].getDeepSameElementInContext == quantified)) {
      for (decl <- clause.declarations) {
        decl match {
          case alias: ScTypeAliasDeclaration => if (!processor.execute(alias, state)) return false
          case valDecl: ScValueDeclaration =>
            for (declared <- valDecl.declaredElements) if (!processor.execute(declared, state)) return false
        }
      }
    }
    true
  }

    override def accept(visitor: ScalaElementVisitor) {
        visitor.visitExistentialTypeElement(this)
      }

      override def accept(visitor: PsiElementVisitor) {
        visitor match {
          case s: ScalaElementVisitor => s.visitExistentialTypeElement(this)
          case _ => super.accept(visitor)
        }
      }
}