package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import _root_.org.jetbrains.plugins.scala.lang.psi.types._
import api.toplevel.typedef.{ScClass, ScTypeDefinition, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi._

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
      case td : ScClass => Some(td.parameters.map {t => t.calcType}) //todo: type inference here
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

  override def calcType = ref.bind match {
    case None => Nothing
    case Some(r) => r.element match {
      case td : ScClass => ScParameterizedType.create(td, r.substitutor)
      case obj : ScObject => new ScDesignatorType (obj)
      case _ => Nothing
    }
  }}