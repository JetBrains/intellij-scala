/**
* @author ven
*/
package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import _root_.scala.collection.mutable.LinkedHashSet
import api.statements.ScTypeAliasDefinition
import api.toplevel.ScNamedElement
import api.toplevel.typedef.{ScTypeDefinition, ScTemplateDefinition}
import collection.mutable.{HashMap, ArrayBuffer, HashSet, Set, ListBuffer}
import com.intellij.psi.{PsiClass}
import psi.types._
import result.TypingContext
import synthetic.ScSyntheticClass

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

  /*def mergeSupers (maps : List[(Map, ScSubstitutor)]) : MultiMap = {
    maps.foldRight(MultiMap.empty) {
      (current, res) => {
        for (p <- current._1) {
          val newSubst = current._2
          val k = p._1 match {
            case phys: PhysicalSignature => {
              new PhysicalSignature(phys.method, phys.substitutor.followed(newSubst)) {
                override def types = phys.types
              }.asInstanceOf[T]
            }
            case full: FullSignature => new FullSignature(full.sig match {
              case phys: PhysicalSignature => new PhysicalSignature(phys.method, phys.substitutor.followed(newSubst))
              case sig: Signature => new Signature(sig.name, sig.typesEval, sig.paramLength, sig.typeParams,
                sig.substitutor.followed(newSubst))
            }, newSubst.subst(full.retType), full.element, full.clazz).asInstanceOf[T]
            case t => t
          }
          val node = new Node(p._2.info match {
            case phys: PhysicalSignature => {
              new PhysicalSignature(phys.method, phys.substitutor.followed(newSubst)).asInstanceOf[T]
            }
            case full: FullSignature => new FullSignature(full.sig match {
              case phys: PhysicalSignature => new PhysicalSignature(phys.method, phys.substitutor.followed(newSubst))
              case sig: Signature => new Signature(sig.name, sig.typesEval, sig.paramLength, sig.typeParams,
                sig.substitutor.followed(newSubst))
            }, newSubst.subst(full.retType), full.element, full.clazz).asInstanceOf[T]
            case t => t
          }, p._2.substitutor.followed(newSubst))
          res.addBinding(k, node)
        }
        res
      }
    }
  }*/

  def mergeSupers (maps : List[Map]) : MultiMap = {
    maps.foldRight(MultiMap.empty) {
      (current, res) => {
        for ((k, node) <- current) {
          res.addBinding(k, node)
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
        case None => nodes.toList(0)
        case Some(concrete) => concrete
      }
      primarySupers += ((key, primarySuper))
      thisMap.get(key) match {
        case Some(node) => {
          node.primarySuper = Some(primarySuper)
          node.supers = nodes.toSeq
        }
        case None => {
          nodes -= primarySuper
          primarySuper.supers = nodes.toSeq
          thisMap += ((key, primarySuper))
        }
      }
    }
    primarySupers
  }

  private def putAliases(template : ScTemplateDefinition, s : ScSubstitutor) = {
    var run = s
    for (alias <- template.aliases) {
      alias match {
        case aliasDef: ScTypeAliasDefinition if s.aliasesMap.get(aliasDef.name) == None =>
          run = run bindA (aliasDef.name, {() => aliasDef.aliasedType(TypingContext.empty).getOrElse(Any)})
        case _ =>
      }
    }
    run
  }

  def build(clazz : PsiClass) : (Map, Map) = {
    val map = new Map
    val superTypesBuff = new ListBuffer[Map]
    //val superTypesBuff = new ListBuffer[(Map, ScSubstitutor)]
    val (superTypes, subst): (Seq[ScType], ScSubstitutor) = clazz match {
      case template : ScTemplateDefinition => {
        processScala(template, ScSubstitutor.empty, map)
        (BaseTypes.get(ScDesignatorType(template)), putAliases(template, ScSubstitutor.empty))
      }
      case syn: ScSyntheticClass => {
        processSyntheticScala(syn, ScSubstitutor.empty, map)
        (syn.getSuperTypes.map{psiType => ScType.create(psiType, syn.getProject)} : Seq[ScType], ScSubstitutor.empty)
      }
      case _ => {
        processJava(clazz, ScSubstitutor.empty, map)
        (BaseTypes.get(ScDesignatorType(clazz)), ScSubstitutor.empty)
      }
    }

    for (superType <- superTypes) {
      def workWithType(superType: ScType) {
        ScType.extractClassType(superType) match {
          case Some((superClass, s)) =>
            // Do not include scala.ScalaObject to Predef's base types to prevent SOE
            if (!(superClass.getQualifiedName == "scala.ScalaObject" && clazz.getQualifiedName == "scala.Predef")) {
              var newSubst = combine(s, subst, superClass)
              val newMap = new Map
              superClass match {
                case template : ScTemplateDefinition => {
                  processScala(template, newSubst, newMap)
                }
                case syn: ScSyntheticClass => {
                  processSyntheticScala(syn, newSubst, newMap)
                }
                case _ => {
                  processJava(superClass, newSubst, newMap)
                }
              }
              superTypesBuff += newMap
            }
          case _ =>
        }
      }
      workWithType(superType)
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
  def processSyntheticScala(clazz : ScSyntheticClass, subst : ScSubstitutor, map : Map)
}
