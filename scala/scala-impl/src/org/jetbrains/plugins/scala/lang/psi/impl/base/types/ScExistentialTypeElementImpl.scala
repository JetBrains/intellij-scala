package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, PsiElementVisitor, ResolveState}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAliasDeclaration, ScValueDeclaration}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.{Nothing, Singleton, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}

import _root_.scala.collection.mutable.ListBuffer

/**
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

class ScExistentialTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScExistentialTypeElement {
  protected def innerType: TypeResult[ScType] = {
    val q = quantified.getType()
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
              alias.typeParameters.map(TypeParameterType(_, None)).toList,
                                               lb.getOrNothing, ub.getOrAny)
          case value: ScValueDeclaration =>
            value.typeElement match {
              case Some(te) =>
                val ttype = te.getType()
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
      failures.foldLeft(this.success(ScExistentialType(t, wildcards)))(_.apply(_))
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