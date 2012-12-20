package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import lang.lexer._
import com.intellij.psi._
import scope.PsiScopeProcessor
import api.ScalaElementVisitor
import psi.types.{ScExistentialArgument, ScParameterizedType, ScType}
import psi.types.result.{TypeResult, Failure, Success, TypingContext}
import api.toplevel.typedef.ScTypeDefinition
import api.statements.params.ScTypeParam

/**
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScTypedPatternImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypedPattern {
  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  def nameId = findChildByType(TokenSets.ID_SET)

  def isWildcard: Boolean = findChildByType(ScalaTokenTypes.tUNDER) != null

  override def isIrrefutableFor(t: Option[ScType]): Boolean = {
    t match {
      case Some(t) => getType(TypingContext.empty) match {
        case Success(tp, _) if t conforms tp => true
        case _ => false
      }
      case _ => false
    }
  }

  override def toString: String = "TypedPattern"

  override def getType(ctx: TypingContext): TypeResult[ScType] = {
    typePattern match {
      case Some(tp) =>
        if (tp.typeElement == null) return Failure("No type element for type pattern", Some(this))
        val typeElementType: TypeResult[ScType] =
          tp.typeElement.getType(ctx).map {
            case tp: ScType =>
              ScType.extractClassType(tp, Some(getProject)) match {  //todo: type aliases?
                case Some((clazz: ScTypeDefinition, subst)) =>
                  val typeParams = clazz.typeParameters
                  tp match {
                    case ScParameterizedType(des, typeArgs) if typeArgs.length == typeParams.length =>
                      ScParameterizedType(des, typeArgs.zip(typeParams).map {
                        case (arg: ScExistentialArgument, param: ScTypeParam) =>
                          val lowerBound =
                            if (arg.lowerBound.equiv(psi.types.Nothing)) subst subst param.lowerBound.getOrNothing
                            else arg.lowerBound //todo: lub?
                          val upperBound =
                            if (arg.upperBound.equiv(psi.types.Any)) subst subst param.upperBound.getOrAny
                            else arg.upperBound //todo: glb?
                          ScExistentialArgument(arg.name, arg.args, lowerBound, upperBound)
                        case (tp: ScType, param: ScTypeParam) => tp
                      })
                    case _ => tp
                  }
                case _ => tp
              }
          }
        expectedType match {
          case Some(expectedType) =>
            typeElementType.map {
              case resType =>
                if (expectedType.conforms(resType)) expectedType
                else { //todo:
                  resType
                }
            }
          case _ => typeElementType
        }
      case None => Failure("No type pattern", Some(this))
    }
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement,
                                   place: PsiElement) = {
    ScalaPsiUtil.processImportLastParent(processor, state, place, lastParent, getType(TypingContext.empty))
  }

  override def getOriginalElement: PsiElement = super[ScTypedPattern].getOriginalElement
}