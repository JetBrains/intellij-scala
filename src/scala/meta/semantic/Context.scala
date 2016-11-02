package scala.meta.semantic

/**
  * @author mucianm 
  * @since 03.06.16.
  */
import scala.{Seq => _}
import scala.collection.immutable.Seq
import scala.meta._

trait Context {
  def dialect: Dialect

  def typecheck(tree: Tree): Tree

  def defns(ref: Ref): Seq[Member]
  def members(tpe: Type): Seq[Member]
  def supermembers(member: Member): Seq[Member]
  def submembers(member: Member): Seq[Member]

  def isSubtype(tpe1: Type, tpe2: Type): Boolean
  def lub(tpes: Seq[Type]): Type
  def glb(tpes: Seq[Type]): Type
  def supertypes(tpe: Type): Seq[Type]
  def widen(tpe: Type): Type
  def dealias(tpe: Type): Type
}
