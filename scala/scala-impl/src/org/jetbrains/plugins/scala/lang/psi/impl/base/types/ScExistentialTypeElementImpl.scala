package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAliasDeclaration, ScValueDeclaration}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{Nothing, Singleton}
import org.jetbrains.plugins.scala.lang.psi.types.result._

class ScExistentialTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScExistentialTypeElement {
  override protected def innerType: TypeResult = {
    /** From SLS 3.2.10
      *
      * Existential Quantification over Values
      *
      * As a syntactic convenience, the bindings clause in an existential type may also contain
      * value declarations val xx: TT.
      * An existential type TT forSome { QQ; val xx: SS;Q′Q′ } is treated as a shorthand
      * for the type T′T′ forSome { QQ; type tt <: SS with Singleton; Q′Q′ },
      * where tt is a fresh type name and T′T′ results from TT by replacing every occurrence of
      * xx.type with tt.
      */
    def withDesugaredValTypes(quantified: ScType): ScType = {
      val valDeclarations = clause.declarations.filterByType[ScValueDeclaration]

      if (valDeclarations.isEmpty) quantified
      else quantified.updateLeaves {
        case des @ ScDesignatorType(named: ScTypedDefinition) =>
          val valueDeclaration = valDeclarations.find(_.declaredElements.contains(named))
          val valType = valueDeclaration.flatMap(_.typeElement).map(_.`type`().getOrAny)
          valType match {
            case Some(tp) =>
              val compound = ScCompoundType(Seq(tp, Singleton))
              val name = s"${named.name}$$type"
              ScExistentialArgument(name, Nil, Nothing, compound)
            case None => des
          }
      }
    }

    quantified.`type`().map { qt =>
      ScExistentialType(withDesugaredValTypes(qt))
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

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitExistentialTypeElement(this)
  }
}