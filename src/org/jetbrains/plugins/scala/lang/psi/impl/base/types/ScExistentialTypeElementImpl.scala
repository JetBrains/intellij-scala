package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import api.toplevel.ScNamedElement
import psi.ScalaPsiElementImpl
import api.base.types._
import api.statements.{ScTypeAliasDeclaration, ScValueDeclaration}
import lang.psi.types._

import com.intellij.lang.ASTNode
import com.intellij.psi.{ResolveState, PsiElement}

import _root_.scala.collection.mutable.ListBuffer
import collection.Set
import result.{TypeResult, Success, Failure, TypingContext}

/**
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

class ScExistentialTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScExistentialTypeElement {
  override def toString: String = "ExistentialType"

  protected def innerType(ctx: TypingContext) = {
    val q = quantified.getType(ctx)
    val problems: ListBuffer[TypeResult[ScType]] = new ListBuffer
    problems += q
    val wildcards: List[ScExistentialArgument] = {
      var buff: ListBuffer[ScExistentialArgument] = new ListBuffer
      for (decl <- clause.declarations) {
        decl match {
          case alias: ScTypeAliasDeclaration => {
            val lb = alias.lowerBound
            val ub = alias.upperBound
            problems += lb; problems += ub
            buff +=  new ScExistentialArgument(alias.name,
                                               alias.typeParameters.map{tp => ScalaPsiManager.typeVariable(tp)}.toList,
                                               lb.getOrElse(Nothing), ub.getOrElse(Any))
          }
          case value: ScValueDeclaration => {
            value.typeElement match {
              case Some(te) =>
                val ttype = te.getType(ctx)
                problems += ttype
                val t = ScCompoundType(Seq(ttype.getOrElse(Any), Singleton), Seq.empty, Seq.empty, ScSubstitutor.empty)
                for (declared <- value.declaredElements) {
                  buff += ScExistentialArgument(declared.name, Nil, Nothing, t)
                }
              case None =>
            }
          }
          case _ =>
        }
      }
      buff.toList
    }
    q flatMap { t =>
      val failures = for (f@Failure(_, _) <- problems) yield f
      failures.foldLeft(Success(ScExistentialTypeReducer.reduce(t, wildcards), Some(this)))(_.apply(_))
    }
  }

  import com.intellij.psi.scope.PsiScopeProcessor

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    if (lastParent == quantified) {
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
}