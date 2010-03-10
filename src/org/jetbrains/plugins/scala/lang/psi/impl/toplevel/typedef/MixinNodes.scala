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
import collection.mutable.{HashMap, ArrayBuffer, HashSet, Set, ListBuffer}
import com.intellij.psi.{PsiClass}
import psi.types._
import result.TypingContext
import synthetic.ScSyntheticClass
import caches.CachesUtil
import com.intellij.psi.util.PsiModificationTracker
import api.toplevel.typedef.{ScObject, ScTypeDefinition, ScTemplateDefinition}

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

  def build(clazz: PsiClass) : (Map, Map) = build(ScDesignatorType(clazz))

  def build(tp : ScType) : (Map, Map) = {
    var isPredef = false
    val map = new Map
    val superTypesBuff = new ListBuffer[Map]
    //val superTypesBuff = new ListBuffer[(Map, ScSubstitutor)]
    val (superTypes, subst): (Seq[ScType], ScSubstitutor) = tp match {
      case ScDesignatorType(template: ScTypeDefinition) => {
        processScala(template, ScSubstitutor.empty, map, false)
        val lin = MixinNodes.linearization(template)
        (if (!lin.isEmpty) lin.tail else lin, putAliases(template, ScSubstitutor.empty))
      }
      case ScDesignatorType(template : ScTemplateDefinition) => {
        processScala(template, ScSubstitutor.empty, map, false)
        (MixinNodes.linearization(template), putAliases(template, ScSubstitutor.empty))
      }
      case ScDesignatorType(syn: ScSyntheticClass) => {
        processSyntheticScala(syn, ScSubstitutor.empty, map)
        (syn.getSuperTypes.map{psiType => ScType.create(psiType, syn.getProject)} : Seq[ScType], ScSubstitutor.empty)
      }
      case ScDesignatorType(clazz: PsiClass) => {
        processJava(clazz, ScSubstitutor.empty, map, false)
        val lin = MixinNodes.linearization(clazz)
        (if (!lin.isEmpty) lin.tail else lin, ScSubstitutor.empty)
      }
      case cp: ScCompoundType => {
        //todo: add processing comp refinement
        (MixinNodes.linearization(cp), ScSubstitutor.empty)
      }
      case _ => (Seq.empty, ScSubstitutor.empty)
    }

    for (superType <- superTypes) {
      def workWithType(superType: ScType) {
        ScType.extractClassType(superType) match {
          case Some((superClass, s)) =>
            // Do not include scala.ScalaObject to Predef's base types to prevent SOE
            if (!(superClass.getQualifiedName == "scala.ScalaObject" && isPredef)) {
              var newSubst = combine(s, subst, superClass)
              val newMap = new Map
              superClass match {
                case template : ScTemplateDefinition => {
                  processScala(template, newSubst, newMap, true)
                }
                case syn: ScSyntheticClass => {
                  processSyntheticScala(syn, newSubst, newMap)
                }
                case _ => {
                  processJava(superClass, newSubst, newMap, true)
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

  def processJava(clazz : PsiClass, subst : ScSubstitutor, map : Map, noPrivates: Boolean)
  def processScala(template : ScTemplateDefinition, subst : ScSubstitutor, map : Map, noPrivates: Boolean)
  def processSyntheticScala(clazz : ScSyntheticClass, subst : ScSubstitutor, map : Map)
}

object MixinNodes {
  def linearization(clazz: PsiClass): Seq[ScType] = {
    /*CachesUtil.get(
      clazz, CachesUtil.LINEARIZATION_KEY,
      new CachesUtil.MyProvider(clazz, {clazz: PsiClass => linearizationInner(clazz)})
        (PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT)
      )*/
    clazz match {
      case obj: ScObject if obj.isPackageObject && obj.getQualifiedName == "scala" => {
        return Seq(ScDesignatorType(obj))
      }
      case _ =>
    }

    val data = clazz.getUserData(CachesUtil.LINEARIZATION_KEY)
    val currModCount = clazz.getManager.getModificationTracker.getOutOfCodeBlockModificationCount
    if (data != null && currModCount == data._2) {
      return data._1
    }
    val linearizationResult = linearizationInner(clazz)
    clazz.putUserData(CachesUtil.LINEARIZATION_KEY, (linearizationResult, currModCount))
    return linearizationResult
  }

  def linearization(compound: ScCompoundType): Seq[ScType] = {
    val comps = compound.components

    //todo: duplicate
    val buffer = new ListBuffer[ScType]
    val set: HashSet[String] = new HashSet //to add here qualified names of classes
    def add(tp: ScType) {
      ScType.extractClass(tp) match {
        case Some(clazz) if clazz.getQualifiedName != null && !set.contains(clazz.getQualifiedName) => {
          tp +: buffer
          set += clazz.getQualifiedName
        }
        case Some(clazz) if clazz.getTypeParameters.length != 0 => {
          val i = buffer.findIndexOf(newTp => {
            ScType.extractClass(newTp) match {
              case Some(newClazz) if newClazz == clazz => true
              case _ => false
            }
          })
          if (i != -1) {
            val newTp = buffer.apply(i)
            if (tp.conforms(newTp)) buffer.update(i, tp)
          }
        }
        case _ =>
      }
    }

    val iterator = comps.reverseIterator
    while (iterator.hasNext) {
      val tp = iterator.next
      ScType.extractClassType(tp) match {
        case Some((clazz, subst)) => {
          val lin = linearization(clazz)
          val newIterator = lin.reverseIterator
          while (newIterator.hasNext) {
            val tp = newIterator.next
            add(subst.subst(tp))
          }
        }
        case _ =>
      }
    }
    return buffer.toSeq
  }

  def linearizationInner(clazz: PsiClass): Seq[ScType] = {
    val tp = {
      if (clazz.getTypeParameters.length == 0) ScDesignatorType(clazz)
      else ScParameterizedType(ScDesignatorType(clazz), clazz.
              getTypeParameters.map(tp => ScalaPsiManager.instance(clazz.getProject).typeVariable(tp)))
    }
    val supers: Seq[ScType] = {
      clazz match {
        case td: ScTemplateDefinition => td.superTypes
        case clazz: PsiClass => clazz.getSuperTypes.map(tp => ScType.create(tp, clazz.getProject)).toSeq
      }
    }
    val buffer = new ListBuffer[ScType]
    val set: HashSet[String] = new HashSet //to add here qualified names of classes
    def add(tp: ScType) {
      ScType.extractClass(tp) match {
        case Some(clazz) if clazz.getQualifiedName != null && !set.contains(clazz.getQualifiedName) => {
          tp +: buffer
          set += clazz.getQualifiedName
        }
        case Some(clazz) if clazz.getTypeParameters.length != 0 => {
          val i = buffer.findIndexOf(newTp => {
            ScType.extractClass(newTp) match {
              case Some(newClazz) if newClazz == clazz => true
              case _ => false
            }
          })
          if (i != -1) {
            val newTp = buffer.apply(i)
            if (tp.conforms(newTp)) buffer.update(i, tp)
          }
        }
        case _ =>
      }
    }

    val iterator = supers.reverseIterator
    while (iterator.hasNext) {
      val tp = iterator.next
      ScType.extractClassType(tp) match {
        case Some((clazz, subst)) => {
          val lin = linearization(clazz)
          val newIterator = lin.reverseIterator
          while (newIterator.hasNext) {
            val tp = newIterator.next
            add(subst.subst(tp))
          }
        }
        case _ =>
      }
    }
    add(tp)
    return buffer.toSeq
  }
}
