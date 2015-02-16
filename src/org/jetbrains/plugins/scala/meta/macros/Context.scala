package org.jetbrains.plugins.scala.meta.macros

import scala.{Seq => _}
import scala.collection.immutable.Seq
import scala.meta._

class Context extends macros.Context {
  override def dialect: Dialect = ???
  override def warning(msg: String) = ???
  override def error(msg: String) = ???
  override def abort(msg: String) = ???
  override def resources = ???

  override def desugar(term: Term): Term = ???
  override def tpe(term: Term): Type = ???
  override def defns(ref: Ref): Seq[Member] = ???
  override def members(tpe: Type): Seq[Member] = ???

  override def isSubType(tpe1: Type, tpe2: Type): Boolean = ???
  override def lub(tpes: Seq[Type]): Type = ???
  override def glb(tpes: Seq[Type]): Type = ???
  override def widen(tpe: Type): Type = ???
  override def parents(tpe: Type): Seq[Type] = ???
  override def dealias(tpe: Type): Type = ???

  override def parents(member: Member): Seq[Member] = ???
  override def children(member: Member): Seq[Member] = ???
}
