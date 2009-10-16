package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import api.base.types._
import api.toplevel.ScNamedElement
import psi.ScalaPsiElementImpl
import lang.psi.types._
import com.intellij.lang.ASTNode
import collection.Set
import result.TypingContext

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScFunctionalTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScFunctionalTypeElement {
  override def toString: String = "FunctionalType"

  override def getType(ctx: TypingContext) = {
    val ret = returnTypeElement match {
      case Some(r) => r.getType(ctx).resType
      case None => Nothing
    }

    paramTypeElement match {
      case tup : ScTupleTypeElement => new ScFunctionType(ret, collection.immutable.Seq(tup.components.map({_.getType(ctx).resType}).toSeq: _*))
      case other: ScTypeElement => {
        val paramTypes = other.getType(ctx).resType match {
          case Unit => Seq.empty
          case t => Seq(t)
        }
        new ScFunctionType(ret, paramTypes)
      }
    }
  }
}