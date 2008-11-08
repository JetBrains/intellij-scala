/**
* @author ven
*/
package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import _root_.scala.collection.mutable.LinkedHashSet
import api.statements.ScTypeAliasDefinition
import api.toplevel.templates.ScExtendsBlock
import api.toplevel.typedef.{ScTypeDefinition, ScMember, ScTemplateDefinition}
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

  //Return primary selected from supersMerged
  def mergeWithSupers(thisMap : Map, supersMerged : MultiMap) = {
    val primarySupers = new Map
    for ((key, nodes) <- supersMerged) {
      val primarySuper = nodes.find {n => !isAbstract(n.info)} match {
        case None => nodes.toArray(0)
        case Some(concrete) => concrete
      }
      primarySupers += ((key, primarySuper))

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
    primarySupers
  }

  private def putAliases(template : ScTemplateDefinition, s : ScSubstitutor) = {
    var run = s
    import Misc.opt2bool
    for (alias <- template.aliases) {
      alias match {
        case aliasDef: ScTypeAliasDefinition if !s.aliasesMap.get(aliasDef.name) =>
          run = run bindA (aliasDef.name, {() => aliasDef.aliasedType})
        case _ =>
      }
    }
    run
  }

  def build(clazz : PsiClass) : (Map, Map) = {
    def inner(clazz: PsiClass, subst: ScSubstitutor, visited: Set[PsiClass]): Map = {
      val map = new Map
      if (visited.contains(clazz)) return map
      visited += clazz

      val (superTypes, s1) = clazz match {
        case tmpl: ScTemplateDefinition => {
          processScala(tmpl, subst, map)
          (tmpl.superTypes, putAliases(tmpl, subst))
        }
        case _ => {
          processJava(clazz, subst, map)
          (clazz.getSuperTypes.map{psiType => ScType.create(psiType, clazz.getProject)}, subst)
        }
      }

      val superTypesBuff = new ListBuffer[Map]
      for (superType <- superTypes) {
        ScType.extractClassType(superType) match {
          case Some((superClass, s)) => superTypesBuff += inner(superClass, combine(s, s1, superClass), visited)
          case _ =>
        }
      }
      mergeWithSupers(map, mergeSupers(superTypesBuff.toList))

      map
    }

    val map = new Map
    val superTypesBuff = new ListBuffer[Map]
    val (superTypes, subst) = clazz match {
      case template : ScTemplateDefinition => {
        processScala(template, ScSubstitutor.empty, map)
        (template.superTypes, putAliases(template, ScSubstitutor.empty))
      }
      case _ => {
        processJava(clazz, ScSubstitutor.empty, map)
        (clazz.getSuperTypes.map{psiType => ScType.create(psiType, clazz.getProject)}, ScSubstitutor.empty)
      }
    }

    for (superType <- superTypes) {
      ScType.extractClassType(superType) match {
        case Some((superClass, s)) =>
          superTypesBuff += inner(superClass, combine(s, subst, superClass), new HashSet[PsiClass])
        case _ =>
      }
    }
    val superMap = mergeWithSupers(map, mergeSupers(superTypesBuff.toList))

    (superMap, map)
  }

  def combine(superSubst : ScSubstitutor, derived : ScSubstitutor, superClass : PsiClass) = {
    var res : ScSubstitutor = ScSubstitutor.empty
    for (tp <- superClass.getTypeParameters) {
      val tv = ScalaPsiManager.typeVariable(tp)
      res = res bindT (tp.getName, derived.subst(superSubst.subst(ScalaPsiManager.typeVariable(tp))))
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
        res = new ScSubstitutor(res.tvMap, aliasesMap, res.outerMap)
      }
      case _ => ()
    }
    res
  }

  def processJava(clazz : PsiClass, subst : ScSubstitutor, map : Map)
  def processScala(template : ScTemplateDefinition, subst : ScSubstitutor, map : Map)
}
