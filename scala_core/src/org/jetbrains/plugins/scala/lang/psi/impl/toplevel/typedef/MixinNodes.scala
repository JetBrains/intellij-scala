package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

abstract class MixinNodes {
  type T

  trait Node {
    val info : T
  }
  
  case class AbstractMember(val info : T) extends Node {
    var overrides : List[AbstractMember] = Nil
  }

  case class ConcreteMember(val info : T) extends Node {
    var overrides : Option[ConcreteMember] = None 
    var implements : List[AbstractMember] = Nil
    var hides : List[ConcreteMember] = Nil
  }
}

import org.jetbrains.plugins.scala.lang.psi.types.Signature
object MethodNodes extends MixinNodes {
  type T = Signature
}

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
object FieldNodes extends MixinNodes {
  type T = ScReferencePattern
}

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
object TypeAliasNodes extends MixinNodes {
  type T = ScTypeAlias
}