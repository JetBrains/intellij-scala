package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import api.statements.{ScTypeAliasDeclaration, ScTypeAliasDefinition, ScTypeAlias}
import api.toplevel.{ScNamedElement, ScPolymorphicElement}
import com.intellij.lang.ASTNode
import com.intellij.psi._
import tree.{IElementType, TokenSet}
import api.base.types._
import api.base.ScReferenceElement
import psi.ScalaPsiElementImpl
import lexer.ScalaTokenTypes
import lang.resolve.ScalaResolveResult
import psi.types._
import psi.impl.toplevel.synthetic.ScSyntheticClass
import collection.Set
import result.{TypeResult, Success, TypingContext}
import scala.None

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 */

class ScSimpleTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScSimpleTypeElement {
  override def toString: String = "SimpleTypeElement"

  def singleton = getNode.findChildByType(ScalaTokenTypes.kTYPE) != null

  protected def innerType(ctx: TypingContext) = {
    val lift: (ScType) => Success[ScType] = Success(_, Some(this))

    val result: TypeResult[ScType] = wrap(reference) flatMap {
      ref => ref.qualifier match {
        case Some(q) => wrap(ref.bind) flatMap {
          case ScalaResolveResult(aliasDef: ScTypeAliasDefinition, s) => {
            if (aliasDef.typeParameters.length == 0) aliasDef.aliasedType(ctx) map {t => s.subst(t)}
            else {
              //todo work with recursive aliases
              lift(new ScTypeConstructorType(aliasDef, s))
            }
          }
          case _ => Success(ScProjectionType(new ScSingletonType(q), ref), Some(this))
        }//Success(ScProjectionType(new ScSingletonType(q), ref), Some(this))
        case None => wrap(ref.bind) flatMap {
          case ScalaResolveResult(e, s) => e match {
            case aliasDef: ScTypeAliasDefinition =>
              if (aliasDef.typeParameters.length == 0) aliasDef.aliasedType(ctx) map {t => s.subst(t)}
              else {
                //todo work with recursive aliases
                lift(new ScTypeConstructorType(aliasDef, s))
              }
            case alias: ScTypeAliasDeclaration => lift(new ScTypeAliasType(alias, s))
            case tp: PsiTypeParameter => lift(ScalaPsiManager.typeVariable(tp))
            case synth: ScSyntheticClass => lift(synth.t)
            case null => lift(Any)
            case _ => lift(ScDesignatorType(e))
          }
        }
      }
    }
    if (result.isEmpty && singleton) {
      Success(ScSingletonType(pathElement), Some(this))
    } else {
      result
    }
  }
}
