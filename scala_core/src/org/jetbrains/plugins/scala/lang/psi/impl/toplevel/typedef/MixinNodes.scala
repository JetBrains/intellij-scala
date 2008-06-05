package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import collection.mutable.{HashMap, ArrayBuffer, Set}

abstract class MixinNodes {
  type T
  def equiv(t1 : T, t2 : T) : Boolean
  def computeHashCode(t : T) : Int
  def isAbstract(t : T) = false//todo
  trait Node {
    val info : T
    var supers : Seq[Node] = Seq.empty
    var primarySuper : Option[Node] = None
  }
  
  class Map extends HashMap[T, Node] {
    override def elemHashCode(k : T) = computeHashCode(k)
    override def elemEquals(t1 : T, t2 : T) = equiv(t1, t2)
  }

  class MultiMap extends HashMap[T, Set[Node]] with collection.mutable.MultiMap[T, Node] {
    override def elemHashCode(k : T) = computeHashCode(k)
    override def elemEquals(t1 : T, t2 : T) = equiv(t1, t2)
  }

  object MultiMap {def empty = new MultiMap}

  def mergeSupers (maps : Map*) : MultiMap = {
    maps.foldLeft(MultiMap.empty){
      (res, curr) => {
        for ((k, node) <- curr) {
          res.add(k, node)
        }
        res
      }
    }
  }

  def mergeWithSupers(thisMap : Map, supersMerged : MultiMap) {
    for ((key, nodes) <- supersMerged) {
      val primarySuper = nodes.find {n => !isAbstract(n.info)} match {
        case None => nodes.toArray(0)
        case Some(concrete) => concrete
      }
      thisMap.get(key) match {
        case Some(node) => {
          node.primarySuper = Some(primarySuper)
          node.supers = nodes.toArray
        }
        case None => {
          nodes -= primarySuper
          primarySuper.supers = nodes.toArray
          thisMap += ((key, primarySuper))
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