package org.jetbrains.plugins.scala.meta.semantic

import scala.meta._
import scala.{Seq => _}
import scala.collection.immutable.Seq

class Context extends semantic.Context {
  override def dialect: Dialect = ???

  override def desugar(term: Term): Term = ???
  override def tpe(term: Term): Type = ???
  override def tpe(param : scala.meta.Term.Param) = ???
  override def defns(ref: Ref): Seq[Member] = ???
  override def members(tpe: Type): Seq[Member] = ???

  override def isSubType(tpe1: Type, tpe2: Type): Boolean = ???
  override def lub(tpes: Seq[Type]): Type = ???
  override def glb(tpes: Seq[Type]): Type = ???
  override def parents(tpe: Type): Seq[Type] = ???
  override def widen(tpe: Type): Type = ???
  override def dealias(tpe: Type): Type = ???

  override def parents(member: Member): Seq[Member] = ???
  override def children(member: Member): Seq[Member] = ???
}
