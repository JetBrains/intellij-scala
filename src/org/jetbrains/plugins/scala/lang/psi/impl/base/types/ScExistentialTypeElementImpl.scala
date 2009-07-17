package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import api.toplevel.ScNamedElement
import psi.ScalaPsiElementImpl
import api.base.types._
import api.statements.{ScTypeAliasDeclaration, ScValueDeclaration}
import lang.psi.types._

import com.intellij.lang.ASTNode
import com.intellij.psi.{ResolveState, PsiElement}

import _root_.scala.collection.mutable.ListBuffer
import collection.Set

/**
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

class ScExistentialTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScExistentialTypeElement {
  override def toString: String = "ExistentialType"

  override def getType(implicit visited: Set[ScNamedElement]) = {
    val q = quantified.getType(visited)
    val wildcards: List[ScExistentialArgument] = {
      var buff: ListBuffer[ScExistentialArgument] = new ListBuffer
      for (decl <- clause.declarations) {
        decl match {
          case alias: ScTypeAliasDeclaration => {
            buff +=  new ScExistentialArgument(alias.name,
                                               alias.typeParameters.map{tp => ScalaPsiManager.typeVariable(tp)}.toList,
                                               alias.lowerBound, alias.upperBound)
          }
          case value: ScValueDeclaration => {
            value.typeElement match {
              case Some(te) =>
                val t = new ScCompoundType(Seq(te.getType(visited).resType, Singleton), Seq.empty, Seq.empty)
                for (declared <- value.declaredElements) {
                  buff += new ScExistentialArgument(declared.name, Nil, Nothing, t)
                }
              case None =>
            }
          }
          case _ =>
        }
      }
      buff.toList
    }

    ScExistentialTypeReducer.reduce(q, wildcards)
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