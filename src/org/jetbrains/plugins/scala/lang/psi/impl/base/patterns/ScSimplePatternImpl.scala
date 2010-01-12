package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import _root_.org.jetbrains.plugins.scala.lang.psi.types._
import api.toplevel.typedef.{ScClass, ScTypeDefinition, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._
import lang.resolve.ScalaResolveResult
import api.base.ScPrimaryConstructor
import api.statements.ScFunction
import result.{TypeResult, TypingContext}

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

class ScConstructorPatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScConstructorPattern {

  override def toString: String = "ConstructorPattern"

  def args = findChildByClass(classOf[ScPatternArgumentList])

  override def subpatterns : Seq[ScPattern]= if (args != null) args.patterns else Seq.empty

  //todo cache
  def bindParamTypes = ref.bind match {
    case None => None
    case Some(r) => r.element match {
      case td : ScClass => Some(td.parameters.map {_.getType(TypingContext.empty).getOrElse(Nothing)}) //todo: type inference here
      case obj : ScObject => { None //todo
        /*val n = args.patterns.length
        for(func <- obj.functionsByName("unapply")) {
          //todo find Option as scala class and substitute its (only) type parameter
        }*/
        //todo unapplySeq
      }
      case _ => None
    }
  }

  override def isIrrefutableFor(t: Option[ScType]): Boolean = {
    if (t == None) return false
    ref.bind match {
      case Some(ScalaResolveResult(clazz: ScClass, _)) if clazz.isCase => {
        ScType.extractClassType(t.get) match {
          case Some((clazz2: ScClass, substitutor: ScSubstitutor)) if clazz2 == clazz => {
            clazz.constructor match {
              case Some(constr: ScPrimaryConstructor) => {
                val clauses = constr.parameterList.clauses
                if (clauses.length == 0) subpatterns.length == 0
                else {
                  val params = clauses(0).parameters
                  if (params.length == 0) return subpatterns.length == 0
                  if (params.length != subpatterns.length) return false  //todo: repeated parameters?
                  var i = 0
                  while (i < subpatterns.length) {
                    val tp = {
                      substitutor.subst(params(i).getType(TypingContext.empty).
                              getOrElse(return false))
                    }
                    if (!subpatterns.apply(i).isIrrefutableFor(Some(tp))) {
                      return false
                    }
                    i = i + 1
                  }
                  true
                }
              }
              case _ => subpatterns.length == 0
            }
          }
          case _ => false
        }
      }
      case _ => false
    }
  }

  override def getType(ctx: TypingContext): TypeResult[ScType] = wrap(ref.bind) map { r =>
    r.element match {
      case td : ScClass => ScParameterizedType.create(td, r.substitutor)
      case obj : ScObject => new ScDesignatorType (obj)
      case fun: ScFunction /*It's unapply method*/ if (fun.getName == "unapply" || fun.getName == "unapplySeq") && fun.parameters.length == 1 => {
        return fun.paramClauses.clauses.apply(0).parameters.apply(0).getType(TypingContext.empty)
      }
      case _ => Nothing
    }
  }

}