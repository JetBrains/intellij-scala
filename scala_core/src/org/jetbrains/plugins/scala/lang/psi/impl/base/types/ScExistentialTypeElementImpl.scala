package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import psi.ScalaPsiElementImpl
import api.base.types._
import api.statements.{ScTypeAliasDeclaration, ScValueDeclaration}
import lang.psi.types._

import com.intellij.lang.ASTNode
import com.intellij.psi.{ResolveState, PsiElement}

import _root_.scala.collection.mutable.ListBuffer


/**
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

class ScExistentialTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScExistentialTypeElement {
  override def toString: String = "ExistentialType"

  override def getType() = {
    val q = quantified.getType
    val wildcards: List[Pair[String, ScWildcardType]] = {
      var buff: ListBuffer[Pair[String, ScWildcardType]] = new ListBuffer
      for (decl <- clause.declarations) {
        decl match {
          case alias: ScTypeAliasDeclaration => {
            buff += ((alias.name, new ScWildcardType(alias.lowerBound, alias.upperBound)))
          }
          case value: ScValueDeclaration => {
            value.typeElement match {
              case Some(te) =>
                val t = new ScCompoundType(Array(te.getType, Singleton), Seq.empty, Seq.empty)
                for (declared <- value.declaredElements) {
                  buff += ((declared.name, new ScWildcardType(Nothing, t)))
                }
              case None =>
            }
          }
          case _ =>
        }
      }
      buff.toList
    }

    if (wildcards.isEmpty) q else {
      q match {
        case ScExistentialType(q, wilds) => new ScExistentialType(q, wilds ::: wildcards)
        case _ => new ScExistentialType(q, wildcards)
      }
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