package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import collection.mutable.{HashMap}

abstract class MixinNodes {
  type T
  def equiv(t1 : T, t2 : T) : Boolean
  def computeHashCode(t : T) : Int
  trait Node {
    val info : T
  }
  
  case class AbstractMember(val info : T) extends Node {
    var overrides : Option[Node] = None //abstract might override concrete
  }

  case class ConcreteMember(val info : T) extends Node {
    var overrides : Option[ConcreteMember] = None
    var implements : Option[AbstractMember] = None
  }

  class Map extends HashMap[T, Node] {
    override def elemHashCode(k : T) = computeHashCode(k)
    override def elemEquals(t1 : T, t2 : T) = equiv(t1, t2)
  }

  object Map {def empty = new Map}

  def mergeSupers (maps : Map*) : Map = {
    maps.foldLeft (Map.empty) {
      (res, curr) =>
        for ((key, node) <- curr) {
          res.get(key) match {
            case None => res += Pair(key, node)
            case Some (node1) =>
              node1 match {
                case a1 : AbstractMember =>
                  node match {
                    case c : ConcreteMember => res += Pair(key, node) //overwrite
                    case _ =>
                  }
                case _ =>
              }
          }
        }
        res
    }
  }

  def mergeWithSupers(thisMap : Map, supersMerged : Map) {
    for ((key, n1) <- supersMerged) {
      thisMap.get(key) match {
        case None => thisMap += Pair(key, n1)
        case Some(n2) =>
          n2 match {
            case a2 : AbstractMember =>
              a2.overrides = Some(n1)
            case c2 : ConcreteMember =>
              n1 match {
                case a1 : AbstractMember => c2.implements = Some(a1)
                case c1 : ConcreteMember => c2.overrides = Some(c1)
              }
          }
      }
    }
  }
}

import org.jetbrains.plugins.scala.lang.psi.types.Signature
class MethodNodes extends MixinNodes {
  type T = Signature
  def equiv(s1 : Signature, s2 : Signature) = s1 equiv s2
  def computeHashCode(s : Signature) = s.name.hashCode* 31 + s.types.length
}

import com.intellij.psi.PsiNamedElement
class FieldNodes extends MixinNodes {
  type T = PsiNamedElement
  def equiv(p1 : PsiNamedElement, p2 : PsiNamedElement) = p1.getName == p2.getName
  def computeHashCode(patt : PsiNamedElement) = patt.getName.hashCode
}

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
class TypeAliasNodes extends MixinNodes {
  type T = ScTypeAlias
  def equiv(al1 : ScTypeAlias, al2 : ScTypeAlias) = al1.name == al2.name
  def computeHashCode(al : ScTypeAlias) = al.name.hashCode
}