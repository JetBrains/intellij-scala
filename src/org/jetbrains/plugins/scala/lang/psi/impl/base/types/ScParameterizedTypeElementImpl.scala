package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.types._
import collection.mutable.HashMap
import result.TypingContext

/**
 * @author Alexander Podkhalyuzin, ilyas
 */

class ScParameterizedTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScParameterizedTypeElement {
  override def toString: String = "ParametrizedTypeElement"

  def typeArgList = findChildByClass(classOf[ScTypeArgs])

  def typeElement = findChildByClass(classOf[ScTypeElement])

  override def getType(ctx: TypingContext) = {
    typeElement.getType(ctx) map { res => {
        //Find cyclic type references
        val argTypesWrapped = typeArgList.typeArgs.map {_.getType(ctx)}
        val argTypes = argTypesWrapped.map {_.resType}
        val cyclic = argTypesWrapped.find(_.isCyclic)
        cyclic match {
          case Some(result) => ScTypeInferenceResult(new ScParameterizedType(res,
            collection.immutable.Seq(argTypes.toSeq: _*)), true, result.cycleStart)
          case None => {
            res match {
              case tp: ScTypeConstructorType => {
                val map = new HashMap[String, ScType]
                for (i <- 0 until argTypes.length.min(tp.args.size)) {
                  map += Tuple(tp.args.apply(i).name, argTypes(i))
                }
                val subst = new ScSubstitutor(Map(map.toSeq: _*), Map.empty, Map.empty)
                subst.subst(tp.aliased.v)
              }
              case _ => {
                new ScParameterizedType(res, collection.immutable.Seq(
                  typeArgList.typeArgs.map({_.getType(ctx).resType}).toSeq: _*
                  ))
              }
            }
          }
        }
      }
    }
  }
}