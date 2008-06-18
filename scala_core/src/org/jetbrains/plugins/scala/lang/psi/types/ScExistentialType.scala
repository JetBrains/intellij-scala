package org.jetbrains.plugins.scala.lang.psi.types

import api.statements.{ScTypeAliasDeclaration, ScValueDeclaration, ScDeclaration}
import _root_.scala.collection.mutable.ListBuffer

/** 
* @author ilyas
*/

case class ScExistentialType(val quantified : ScType, decls : Seq[ScDeclaration]) extends ScType {
  val wildcards : List[Pair[String, ScWildcardType]] = {
    var buff : ListBuffer[Pair[String, ScWildcardType]] = new ListBuffer
    for (decl <- decls) {
      decl match {
        case alias : ScTypeAliasDeclaration => {
          buff += ((alias.name, new ScWildcardType(alias.lowerBound, alias.upperBound)))
        }
        case value : ScValueDeclaration => {
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

  def boundNames = wildcards.map {_._1}

  def substitutor = wildcards.foldLeft(ScSubstitutor.empty) {(s, p) => s + (p._1, p._2)}
  
  override def equiv(t : ScType) = t match {
    case ex : ScExistentialType => wildcards.equalsWith(ex.wildcards) {_._2 equiv _._2} &&
            (substitutor.subst(quantified) equiv ex.substitutor.subst(ex.quantified))
    case _ => false
  }
}

case class ScWildcardType(val lowerBound : ScType, val upperBound : ScType) extends ScType {
  override def equiv(t : ScType) = t match {
    case wild : ScWildcardType => lowerBound.equiv(wild.lowerBound) && upperBound.equiv(wild.upperBound)
  }
}