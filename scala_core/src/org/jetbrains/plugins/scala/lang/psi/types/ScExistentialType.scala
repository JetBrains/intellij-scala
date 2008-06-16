package org.jetbrains.plugins.scala.lang.psi.types

import api.statements.{ScTypeAlias, ScValueDeclaration}

/** 
* @author ilyas
*/

case class ScExistentialType(val quantified : ScType, aliases : Seq[ScTypeAlias], vals : Seq[ScValueDeclaration]) extends ScType {
  val substitutor : ScSubstitutor = {
    var map : Map[String, ScType] = Map.empty
    for (alias <- aliases) {
      map = map + ((alias.name, new ScWildcardType(alias.lowerBound, alias.upperBound)))
    }
    for (value <- vals) {
      value.typeElement match {
        case Some (te) =>
          val t = new ScCompoundType(Array(te.getType, Singleton), Seq.empty, Seq.empty)
          for (declared <- value.declaredElements) {
            map = map + ((declared.name, new ScWildcardType(Nothing, t)))
          }
        case None =>
      }
    }
    new ScSubstitutor(Map.empty, map)
  }
}

case class ScWildcardType(val lowerBound : ScType, val upperBound : ScType) extends ScType