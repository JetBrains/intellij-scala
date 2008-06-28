package org.jetbrains.plugins.scala.lang.psi.types

import api.statements.{ScTypeAliasDeclaration, ScValueDeclaration, ScDeclaration}
import _root_.scala.collection.mutable.ListBuffer

/** 
* @author ilyas
*/

object ScExistentialType {
  def create(quantified : ScType, decls : Seq[ScDeclaration]) = {
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
    new ScExistentialType(quantified, wildcards)
  }
}

case class ScExistentialType(val quantified : ScType,
                             val wildcards : List[Pair[String, ScWildcardType]]) extends ScType {
  val boundNames = wildcards.map {_._1}
  val boundTypes = wildcards.map {_._2}

  def substitutor = wildcards.foldLeft(ScSubstitutor.empty) {(s, p) => s + (p._1, p._2)}
  
  override def equiv(t : ScType) = t match {
    case ex : ScExistentialType => {
        val unify = (ex.boundNames zip boundTypes).foldLeft(ScSubstitutor.empty) {(s, p) => s + (p._1, p._2)}
        wildcards.equalsWith(ex.wildcards) {(w1, w2) => w1._2.equiv(unify.subst(w2._2))}
      } && (substitutor.subst(quantified) equiv ex.substitutor.subst(ex.quantified))
    case _ => false
  }
}

case class ScWildcardType(val lowerBound : ScType, val upperBound : ScType) extends ScType {
  override def equiv(t : ScType) = t match {
    case wild : ScWildcardType => lowerBound.equiv(wild.lowerBound) && upperBound.equiv(wild.upperBound)
  }
}