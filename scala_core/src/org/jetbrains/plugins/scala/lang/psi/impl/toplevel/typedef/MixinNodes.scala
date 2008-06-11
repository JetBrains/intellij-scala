package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import collection.mutable.{HashMap, ArrayBuffer, HashSet, Set, ListBuffer}
import com.intellij.psi.PsiClass
import api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types._

abstract class MixinNodes {
  type T
  def equiv(t1 : T, t2 : T) : Boolean
  def computeHashCode(t : T) : Int
  def isAbstract(t : T) : Boolean
  class Node (val info : T) {
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

  def mergeSupers (maps : List[Map]) : MultiMap = {
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

  def build(td : ScTypeDefinition) = {
    def inner(clazz : PsiClass, subst : ScSubstitutor, visited : Set[PsiClass]) : Map = {
      val map = new Map
      if (visited.contains(clazz)) return map
      visited += clazz

      val superTypes = clazz match {
        case td : ScTypeDefinition => {
          processScala(td, subst, map)
          td.superTypes
        }
        case _ => {
          processJava(clazz, subst, map)
          clazz.getSuperTypes.map {psiType => ScType.create(psiType, clazz.getProject)}
        }
      }

      val superTypesBuff = new ListBuffer[Map]
      for (superType <- superTypes) {
        superType match {
          case ScParameterizedType(superClass : PsiClass, superSubst) => {
            superTypesBuff += inner (superClass, combine(superSubst, subst), visited)
          }
          case _ =>
        }
      }
      mergeWithSupers(map, mergeSupers(superTypesBuff.toList))

      map
    }
    inner(td, ScSubstitutor.empty, new HashSet[PsiClass])
  }

  def combine(superSubst : ScSubstitutor, derived : ScSubstitutor) = {
    var res : ScSubstitutor = ScSubstitutor.empty
    for ((tp, t) <- superSubst.map) {
      res = res + (tp, derived.subst(t))
    }
    res
  }

  def processJava(clazz : PsiClass, subst : ScSubstitutor, map : Map)
  def processScala(td : ScTypeDefinition, subst : ScSubstitutor, map : Map)
}
