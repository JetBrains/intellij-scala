package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.types._


/**
 * @author Alexander Podkhalyuzin
 * Date: 13.03.2008
 */

class ScParameterizedTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScParameterizedTypeElement {
  override def toString: String = "ParametrizedTypeElement"

  def typeArgList = findChildByClass(classOf[ScTypeArgs])

  def typeElement = findChildByClass(classOf[ScTypeElement])

  override def getType(implicit visited: Set[ScNamedElement]) = {
    typeElement.getType(visited) match {
      case r@ScTypeInferenceResult(_, true, _) => r
      //todo think about recursive types
      case ScTypeInferenceResult(res, false, _) => {
        //Find cyclic type reference
        val argTypesWrapped = typeArgList.typeArgs.map{_.getType(visited)}
        val argTypes = argTypesWrapped.map{_.resType}
        val cyclic = argTypesWrapped.find(_.isCyclic)
        cyclic match {
          case Some(result) => ScTypeInferenceResult(new ScParameterizedType(res, argTypes.toArray), true, result.cycleStart)
          case None => new ScParameterizedType(res, typeArgList.typeArgs.map{_.getType(visited).resType}.toArray)
        }
      }
    }
  }
}