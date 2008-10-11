/**
* @author ven
*/
package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import _root_.scala.collection.mutable.LinkedHashSet
import api.toplevel.templates.ScExtendsBlock
import api.toplevel.typedef.{ScTypeDefinition, ScMember}
import collection.mutable.{HashMap, ArrayBuffer, HashSet, Set, ListBuffer}
import com.intellij.psi.{PsiElement, PsiClass}
import psi.types._

abstract class MixinNodes {
  type T
  def equiv(t1 : T, t2 : T) : Boolean
  def computeHashCode(t : T) : Int
  def isAbstract(t : T) : Boolean
  class Node (val info : T, val substitutor : ScSubstitutor) {
    var supers : Seq[Node] = Seq.empty
    var primarySuper : Option[Node] = None
  }
  
  class Map extends HashMap[T, Node] {
    override def elemHashCode(t : T) = computeHashCode(t)
    override def elemEquals(t1 : T, t2 : T) = equiv(t1, t2)
  }

  class MultiMap extends HashMap[T, Set[Node]] with collection.mutable.MultiMap[T, Node] {
    override def elemHashCode(t : T) = computeHashCode(t)
    override def elemEquals(t1 : T, t2 : T) = equiv(t1, t2)

    override def makeSet = new LinkedHashSet[Node]
  }

  object MultiMap {def empty = new MultiMap}

  def mergeSupers (maps : List[Map]) : MultiMap = {
    maps.foldRight(MultiMap.empty){
      (curr, res) => {
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

  def build (clazz : PsiClass) : Map = build { map =>
    processJava(clazz, ScSubstitutor.empty, map)
    clazz.getSuperTypes.map{psiType => ScType.create(psiType, clazz.getProject)}
  }

  def build (eb : ScExtendsBlock) : Map = build { map =>
    processScala(eb.members, ScSubstitutor.empty, map)
    eb.superTypes
  }

  private def build(superTypesFun : Map => Seq[ScType]) = {
    def inner(clazz: PsiClass, subst: ScSubstitutor, visited: Set[PsiClass]): Map = {
      val map = new Map
      if (visited.contains(clazz)) return map
      visited += clazz

      val superTypes = clazz match {
        case td: ScTypeDefinition => {
          processScala(td.members, subst, map)
          td.superTypes
        }
        case _ => {
          processJava(clazz, subst, map)
          clazz.getSuperTypes.map{psiType => ScType.create(psiType, clazz.getProject)}
        }
      }

      val superTypesBuff = new ListBuffer[Map]
      for (superType <- superTypes) {
        ScType.extractClassType(superType) match {
          case Some((superClass, s)) => superTypesBuff += inner(superClass, combine(s, subst, superClass), visited)
          case _ =>
        }
      }
      mergeWithSupers(map, mergeSupers(superTypesBuff.toList))

      map
    }

    val map = new Map
    val superTypesBuff = new ListBuffer[Map]
    for (superType <- superTypesFun(map)) {
      ScType.extractClassType(superType) match {
        case Some((superClass, s)) => superTypesBuff += inner(superClass, s, new HashSet[PsiClass])
        case _ =>
      }
    }
    mergeWithSupers(map, mergeSupers(superTypesBuff.toList))

    map
  }

  def combine(superSubst : ScSubstitutor, derived : ScSubstitutor, superClass : PsiClass) = {
    var res : ScSubstitutor = ScSubstitutor.empty
    for (tp <- superClass.getTypeParameters) {
      val tv = ScalaPsiManager.typeVariable(tp)
      res = res + (tv, derived.subst(superSubst.subst(tv)))
    }
    superClass match {
      case td : ScTypeDefinition => {
        var aliasesMap = res.aliasesMap
        for (alias <- td.aliases) {
          derived.aliasesMap.get(alias.name) match {
            case Some(t) => aliasesMap = aliasesMap + ((alias.name, t))
            case None =>
          }
        }
        res = new ScSubstitutor(res.tvMap, res.outerMap, aliasesMap)
      }
      case _ => ()
    }
    res
  }

  def processJava(clazz : PsiClass, subst : ScSubstitutor, map : Map)
  def processScala(members : Seq[ScMember], subst : ScSubstitutor, map : Map)
}
