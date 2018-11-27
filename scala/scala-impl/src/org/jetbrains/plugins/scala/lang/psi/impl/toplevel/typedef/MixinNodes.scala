/**
  * @author ven
  */
package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.{PsiClass, PsiClassType, PsiElement}
import com.intellij.util.containers.{ContainerUtil, SmartHashSet}
import gnu.trove.TIntObjectHashMap
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.macroAnnotations.CachedWithRecursionGuard
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.generic.FilterMonadic
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}

abstract class MixinNodes {
  type T

  private type SigToSuper = (T, Node)

  def equiv(t1: T, t2: T): Boolean
  def same(t1: T, t2: T): Boolean
  def computeHashCode(t: T): Int
  def elemName(t: T): String
  def isAbstract(t: T): Boolean
  def isImplicit(t: T): Boolean
  def isPrivate(t: T): Boolean

  class Node(val info: T, val substitutor: ScSubstitutor) {
    var supers: Seq[Node] = Seq.empty
    var primarySuper: Option[Node] = None
  }

  class Map {
    private[Map] val implicitNames: SmartHashSet[String] = new SmartHashSet[String]
    private val publicsMap: mutable.HashMap[String, NodesMap] = mutable.HashMap.empty
    private val privatesMap: mutable.HashMap[String, ArrayBuffer[SigToSuper]] = mutable.HashMap.empty
    def addToMap(key: T, node: Node) {
      val name = ScalaNamesUtil.clean(elemName(key))
      if (isPrivate(key)) {
        privatesMap.getOrElseUpdate(name, ArrayBuffer.empty) += ((key, node))
      }
      else {
        val nodesMap = publicsMap.getOrElseUpdate(name, new NodesMap)
        nodesMap.update(key, node)
      }
      if (isImplicit(key)) implicitNames.add(name)
    }

    def publicNames: collection.Set[String] = publicsMap.keySet

    def addPublicsFrom(map: Map): Unit = publicsMap ++= map.publicsMap

    @volatile
    private var supersList: List[Map] = List.empty
    def setSupersMap(list: List[Map]) {
      for (m <- list) {
        implicitNames.addAll(m.implicitNames)
      }
      supersList = list
    }

    private val thisAndSupersMap = ContainerUtil.newConcurrentMap[String, (AllNodes, AllNodes)]()

    def forName(name: String): (AllNodes, AllNodes) = {
      val convertedName = ScalaNamesUtil.clean(name)
      def calculate: (AllNodes, AllNodes) = {
        val thisMap: NodesMap = publicsMap.getOrElse(convertedName, NodesMap.empty)
        val maps: List[NodesMap] = supersList.map(sup => sup.publicsMap.getOrElse(convertedName, NodesMap.empty))
        val supers = mergeWithSupers(thisMap, mergeSupers(maps))
        val list = supersList.flatMap(_.privatesMap.getOrElse(convertedName, Nil))
        val supersPrivates = toNodesSeq(list)
        val thisPrivates = toNodesSeq(privatesMap.getOrElse(convertedName, Nil).toList ::: list)
        val thisAllNodes = new AllNodes(thisMap, thisPrivates)
        val supersAllNodes = new AllNodes(supers, supersPrivates)
        (thisAllNodes, supersAllNodes)
      }
      thisAndSupersMap.atomicGetOrElseUpdate(convertedName, calculate)
    }

    @volatile
    private var forImplicitsCache: List[SigToSuper] = null

    def forImplicits(): List[SigToSuper] = {
      def implicitEntry(sigToSuper: SigToSuper): Option[SigToSuper] = {
        val (sig, primarySuper) = sigToSuper

        val nonAbstract =
          if (isAbstract(sig) && !isAbstract(primarySuper.info)) primarySuper.info
          else sig

        if (isImplicit(nonAbstract))
          Some((nonAbstract, primarySuper))
        else None
      }

      if (forImplicitsCache != null) return forImplicitsCache

      val res = new ArrayBuffer[SigToSuper]()
      val iterator = implicitNames.iterator()
      while (iterator.hasNext) {
        val thisMap = forName(iterator.next)._1
        res ++= thisMap.flatMap(implicitEntry)
      }
      forImplicitsCache = res.toList
      forImplicitsCache
    }

    def allNames(): Set[String] = {
      val names = new mutable.HashSet[String]
      names ++= publicsMap.keySet
      names ++= privatesMap.keySet
      for (sup <- supersList) {
        names ++= sup.publicsMap.keySet
        names ++= sup.privatesMap.keySet
      }
      names.toSet
    }

    private def computeForAllNames(): Unit = allNames().foreach(forName)

    def allFirstSeq(): Seq[AllNodes] = {
      computeForAllNames()
      thisAndSupersMap.values().asScala.map(_._1).toSeq
    }

    def allSecondSeq(): Seq[AllNodes] = {
      computeForAllNames()
      thisAndSupersMap.values().asScala.map(_._2).toSeq
    }

    private def toNodesSeq(seq: List[SigToSuper]): NodesSeq = {
      val map = new TIntObjectHashMap[List[SigToSuper]]()
      for (elem <- seq) {
        val key = computeHashCode(elem._1)
        val prev = map.getOrElse(key, List.empty)
        map.put(key, elem :: prev)
      }
      new NodesSeq(map)
    }

    private class MultiMap extends mutable.HashMap[T, mutable.Set[Node]] with collection.mutable.MultiMap[T, Node] {
      override def elemHashCode(t : T): Int = computeHashCode(t)
      override def elemEquals(t1 : T, t2 : T): Boolean = equiv(t1, t2)
      override def makeSet = new mutable.LinkedHashSet[Node]
    }

    private object MultiMap {def empty = new MultiMap}

    private def mergeSupers(maps: List[NodesMap]) : MultiMap = {
      val res = MultiMap.empty
      val mapsIterator = maps.iterator
      while (mapsIterator.hasNext) {
        val currentIterator = mapsIterator.next().iterator
        while (currentIterator.hasNext) {
          val (k, node) = currentIterator.next()
          res.addBinding(k, node)
        }
      }
      res
    }

    //Return primary selected from supersMerged
    private def mergeWithSupers(thisMap: NodesMap, supersMerged: MultiMap): NodesMap = {
      val primarySupers = new NodesMap
      for ((key, nodes) <- supersMerged) {
        val primarySuper = nodes.find {n => !isAbstract(n.info)} match {
          case None => nodes.toList.head
          case Some(concrete) => concrete
        }
        primarySupers += ((key, primarySuper))
        thisMap.get(key) match {
          case Some(node) =>
            node.primarySuper = Some(primarySuper)
            node.supers = nodes.toSeq
          case None =>
            nodes -= primarySuper
            primarySuper.supers = nodes.toSeq
            thisMap += ((key, primarySuper))
        }
      }
      primarySupers
    }
  }

  class AllNodes(publics: NodesMap, privates: NodesSeq) {
    def get(s: T): Option[Node] = {
      publics.get(s) match {
        case res: Some[Node] => res
        case _ => privates.get(s)
      }
    }

    def foreach(p: SigToSuper => Unit) {
      publics.foreach(p)
      privates.map.flattenValues.foreach(p)
    }

    def map[R](p: SigToSuper => R): Seq[R] = {
      publics.map(p).toSeq ++ privates.map.flattenValues.map(p)
    }

    def filter(p: SigToSuper => Boolean): Seq[SigToSuper] = {
      publics.filter(p).toSeq ++ privates.map.flattenValues.filter(p)
    }

    def withFilter(p: SigToSuper => Boolean): FilterMonadic[SigToSuper, Seq[SigToSuper]] = {
      (publics.toSeq ++ privates.map.flattenValues).withFilter(p)
    }

    def flatMap[R](p: SigToSuper => Traversable[R]): Seq[R] = {
      publics.flatMap(p).toSeq ++ privates.map.flattenValues.flatMap(p)
    }

    def iterator: Iterator[SigToSuper] = {
      new Iterator[SigToSuper] {
        private val iter1 = publics.iterator
        private val iter2 = privates.map.flattenValues.iterator
        def hasNext: Boolean = iter1.hasNext || iter2.hasNext

        def next(): SigToSuper = if (iter1.hasNext) iter1.next() else iter2.next()
      }
    }

    def fastPhysicalSignatureGet(key: T): Option[Node] = {
      publics.fastPhysicalSignatureGet(key) match {
        case res: Some[Node] => res
        case _ => privates.get(key)
      }
    }

    def isEmpty: Boolean = publics.isEmpty && privates.map.forEachValue(_.isEmpty)
  }

  class NodesSeq(private[MixinNodes] val map: TIntObjectHashMap[List[SigToSuper]]) {
    def add(s: T, node: Node): Unit = {
      val key = computeHashCode(s)
      val prev = map.get(key)
      map.put(key, (s, node) :: prev)
    }

    def addAll(list: List[SigToSuper]): Unit = {
      list.foreach {
        case (s, node) => add(s, node)
      }
    }

    def get(s: T): Option[Node] = {
      val list = map.getOrElse(computeHashCode(s), Nil)
      val iterator = list.iterator
      while (iterator.hasNext) {
        val next = iterator.next()
        if (same(s, next._1)) return Some(next._2)
      }
      None
    }
  }

  class NodesMap extends mutable.HashMap[T, Node] {
    override def elemHashCode(t: T): Int = computeHashCode(t)
    override def elemEquals(t1 : T, t2 : T): Boolean = equiv(t1, t2)

    /**
      * Use this method if you are sure, that map contains key
      */
    def fastGet(key: T): Option[Node] = {
      //todo: possible optimization to filter without types first then if only one variant left, get it.
      val h = index(elemHashCode(key))
      var e = table(h).asInstanceOf[Entry]
      if (e != null && e.next == null) return Some(e.value)
      while (e != null) {
        if (elemEquals(e.key, key)) return Some(e.value)
        e = e.next
        if (e.next == null) return Some(e.value)
      }
      None
    }

    def fastPhysicalSignatureGet(key: T): Option[Node] = {
      key match {
        case p: PhysicalSignature =>
          val h = index(elemHashCode(key))
          var e = table(h).asInstanceOf[Entry]
          if (e != null && e.next == null) {
            e.value.info match {
              case p2: PhysicalSignature =>
                if (p.method == p2.method) return Some(e.value)
                else return None
              case _ => return None
            }
          }
          while (e != null) {
            e.value.info match {
              case p2: PhysicalSignature =>
                if (p.method == p2.method) return Some(e.value)
              case _ =>
            }
            e = e.next
          }
          fastGet(key)
        case _ => fastGet(key)
      }
    }
  }

  object NodesMap {
    def empty: NodesMap = new NodesMap()
  }

  def emptyMap: Map = new Map()

  def build(clazz: PsiClass): Map = {
    if (!clazz.isValid) emptyMap
    else {
      build(ScalaType.designator(clazz))(clazz)
    }
  }

  def build(tp: ScType, compoundThisType: Option[ScType] = None)
           (implicit ctx: ProjectContext): Map = {
    var isPredef = false
    var place: Option[PsiElement] = None
    val map = new Map
    val superTypesBuff = new ListBuffer[Map]
    val (superTypes, thisTypeSubst): (Seq[ScType], ScSubstitutor) = tp match {
      case cp: ScCompoundType =>
        processRefinement(cp, map, place)
        val thisTypeSubst = compoundThisType match {
          case Some(comp) => ScSubstitutor(comp)
          case _ => ScSubstitutor(tp)
        }
        (MixinNodes.linearization(cp), thisTypeSubst)
      case _ =>
        val clazz = tp match {
          case ScDesignatorType(clazz: PsiClass) => clazz
          case ScProjectionType(_, clazz: PsiClass) => clazz
          case _ => null
        }
        if (clazz == null) (Seq.empty, ScSubstitutor.empty)
        else
          clazz match {
            case template: ScTypeDefinition =>
              if (template.qualifiedName == "scala.Predef") isPredef = true
              place = Option(template.extendsBlock)
              processScala(template, ScSubstitutor.empty, map, place, base = true)
              val lin = MixinNodes.linearization(template)
              var zSubst = ScSubstitutor(ScThisType(template))
              var placer = template.getContext
              while (placer != null) {
                placer match {
                  case t: ScTemplateDefinition => zSubst = zSubst.followed(
                    ScSubstitutor(ScThisType(t))
                  )
                  case _ =>
                }
                placer = placer.getContext
              }
              (if (lin.nonEmpty) lin.tail else lin, zSubst)
            case template: ScTemplateDefinition =>
              place = template.lastChildStub
              processScala(template, ScSubstitutor.empty, map, place, base = true)
              var zSubst = ScSubstitutor(ScThisType(template))
              var placer = template.getContext
              while (placer != null) {
                placer match {
                  case t: ScTemplateDefinition => zSubst = zSubst.followed(
                    ScSubstitutor(ScThisType(t))
                  )
                  case _ =>
                }
                placer = placer.getContext
              }
              (MixinNodes.linearization(template), zSubst)
            case syn: ScSyntheticClass =>
              (syn.getSuperTypes.map { psiType => psiType.toScType() }: Seq[ScType], ScSubstitutor.empty)
            case clazz: PsiClass =>
              place = Option(clazz.getLastChild)
              processJava(clazz, ScSubstitutor.empty, map, place)
              val lin = MixinNodes.linearization(clazz)
              (if (lin.nonEmpty) lin.tail else lin, ScSubstitutor.empty)
            case _ =>
              (Seq.empty, ScSubstitutor.empty)
          }
    }
    val iter = superTypes.iterator
    while (iter.hasNext) {
      val superType = iter.next()
      superType.extractClassType match {
        case Some((superClass, s)) =>
          // Do not include scala.ScalaObject to Predef's base types to prevent SOE
          if (!(superClass.qualifiedName == "scala.ScalaObject" && isPredef)) {
            val dependentSubst = superType match {
              case p@ScProjectionType(proj, _) => ScSubstitutor(proj).followed(p.actualSubst)
              case ParameterizedType(p@ScProjectionType(proj, _), _) => ScSubstitutor(proj).followed(p.actualSubst)
              case _ => ScSubstitutor.empty
            }
            val newSubst = combine(s, superClass).followed(thisTypeSubst).followed(dependentSubst)
            val newMap = new Map
            superClass match {
              case template: ScTemplateDefinition => processScala(template, newSubst, newMap, place, base = false)
              case syn: ScSyntheticClass =>
                //it's required to do like this to have possibility mix Synthetic types
                syn.elementScope.getCachedClass(syn.getQualifiedName)
                  .collect {
                    case template: ScTemplateDefinition => template
                  }.foreach {
                  processScala(_, newSubst, newMap, place, base = false)
                }
              case _ => processJava(superClass, newSubst, newMap, place)
            }
            superTypesBuff += newMap
          }
        case _ =>
      }
      (superType.isAliasType match {
        case Some(AliasType(_: ScTypeAliasDefinition, lower, _)) => lower.getOrElse(superType)
        case _ => superType
      }) match {
        case c: ScCompoundType =>
          processRefinement(c, map, place)
        case _ =>
      }
    }
    map.setSupersMap(superTypesBuff.toList)
    map
  }

  def combine(superSubst: ScSubstitutor, superClass : PsiClass): ScSubstitutor = {
    val typeParameters = superClass.getTypeParameters
    val substedTpts = typeParameters.map(tp => superSubst(TypeParameterType(tp)))
    ScSubstitutor.bind(typeParameters, substedTpts)
  }

  def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement])

  def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, map: Map,
                   place: Option[PsiElement], base: Boolean)

  def processRefinement(cp: ScCompoundType, map: Map, place: Option[PsiElement])(implicit ctx: ProjectContext)
}

object MixinNodes {
  def linearization(clazz: PsiClass): Seq[ScType] = {
    @CachedWithRecursionGuard(clazz, Seq.empty, CachesUtil.libraryAwareModTracker(clazz))
    def inner(): Seq[ScType] = {
      implicit val ctx: ProjectContext = clazz

      clazz match {
        case obj: ScObject if obj.isPackageObject && obj.qualifiedName == "scala" =>
          Seq(ScalaType.designator(obj))
        case _ =>
          ProgressManager.checkCanceled()
          val tp = {
            def default =
              if (clazz.getTypeParameters.isEmpty) ScalaType.designator(clazz)
              else ScParameterizedType(ScalaType.designator(clazz),
                clazz.getTypeParameters.map(TypeParameterType(_)))
            clazz match {
              case td: ScTypeDefinition => td.`type`().getOrElse(default)
              case _ => default
            }
          }
          val supers: Seq[ScType] = {
            clazz match {
              case td: ScTemplateDefinition => td.superTypes
              case clazz: PsiClass => clazz.getSuperTypes.map {
                case ctp: PsiClassType =>
                  //noinspection ScalaRedundantCast
                  val cl = ctp.resolve().asInstanceOf[PsiClass]
                  if (cl != null && cl.qualifiedName == "java.lang.Object") ScDesignatorType(cl)
                  else ctp.toScType()
                case ctp => ctp.toScType()
              }.toSeq
            }
          }

          generalLinearization(tp, addTp = true, supers = supers)
      }

    }

    inner()
  }


  def linearization(compound: ScCompoundType, addTp: Boolean = false)
                   (implicit ctx: ProjectContext): Seq[ScType] = {
    val comps = compound.components

    generalLinearization(compound, addTp = addTp, supers = comps)
  }


  private def generalLinearization(tp: ScType, addTp: Boolean, supers: Seq[ScType])
                                  (implicit ctx: ProjectContext): Seq[ScType] = {
    val buffer = new ListBuffer[ScType]
    val set: mutable.HashSet[String] = new mutable.HashSet //to add here qualified names of classes
    def classString(clazz: PsiClass): String = {
      clazz match {
        case obj: ScObject => "Object: " + obj.qualifiedName
        case tra: ScTrait => "Trait: " + tra.qualifiedName
        case _ => "Class: " + clazz.qualifiedName
      }
    }
    def add(tp: ScType) {
      tp.extractClass match {
        case Some(clazz) if clazz.qualifiedName != null && !set.contains(classString(clazz)) =>
          tp +=: buffer
          set += classString(clazz)
        case Some(clazz) if clazz.getTypeParameters.nonEmpty =>
          val i = buffer.indexWhere(_.extractClass match {
            case Some(newClazz) if ScEquivalenceUtil.areClassesEquivalent(newClazz, clazz) => true
            case _ => false
          }
          )
          if (i != -1) {
            val newTp = buffer.apply(i)
            if (tp.conforms(newTp)) buffer.update(i, tp)
          }
        case _ =>
          (tp.isAliasType match {
            case Some(AliasType(_: ScTypeAliasDefinition, lower, _)) => lower.getOrElse(tp)
            case _ => tp
          }) match {
            case c: ScCompoundType => c +=: buffer
            case _ =>
          }
      }
    }

    val iterator = supers.iterator
    while (iterator.hasNext) {
      var tp = iterator.next()
      @tailrec
      def updateTp(tp: ScType, depth: Int = 0): ScType = {
        tp.isAliasType match {
          case Some(AliasType(_, _, Right(upper))) =>
            if (tp != upper && depth < 100) updateTp(upper, depth + 1)
            else tp
          case _ =>
            tp match {
              case ex: ScExistentialType => ex.quantified
              case tpt: TypeParameterType => tpt.upperType
              case _ => tp
            }
        }
      }
      tp = updateTp(tp)
      tp.extractClassType match {
        case Some((clazz, subst)) =>
          val lin = linearization(clazz)
          val newIterator = lin.reverseIterator
          while (newIterator.hasNext) {
            val tp = newIterator.next()
            add(subst(tp))
          }
        case _ =>
          (tp.isAliasType match {
            case Some(AliasType(_: ScTypeAliasDefinition, lower, _)) => lower.getOrElse(tp)
            case _ => tp
          }) match {
            case c: ScCompoundType =>
              val lin = linearization(c, addTp = true)
              val newIterator = lin.reverseIterator
              while (newIterator.hasNext) {
                val tp = newIterator.next()
                add(tp)
              }

            case _ =>
          }
      }
    }
    if (addTp) add(tp)
    buffer
  }

  private implicit class TIntListHashMapOps[T](val map: TIntObjectHashMap[List[T]]) extends AnyVal {
    def getOrElse(key: Int, t: List[T]): List[T] = {
      val fromMap = map.get(key)

      if (fromMap != null) fromMap
      else t
    }

    def flattenValues: Seq[T] = {
      val result = ArrayBuffer[T]()

      map.forEachValue(list => {
        result ++= list
        true
      })
      result
    }
  }
}
