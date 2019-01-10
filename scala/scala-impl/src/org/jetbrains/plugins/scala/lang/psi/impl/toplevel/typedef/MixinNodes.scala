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
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.macroAnnotations.CachedWithRecursionGuard
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.generic.FilterMonadic
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}

abstract class MixinNodes[T: SignatureStrategy] {
  type Map = MixinNodes.Map[T]
  type Node = MixinNodes.Node[T]

  def shouldSkip(t: T): Boolean

  def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement])

  def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, map: Map, place: Option[PsiElement])

  def processRefinement(cp: ScCompoundType, map: Map, place: Option[PsiElement])

  final def addToMap(t: T, node: Node, m: Map): Unit =
    if (!shouldSkip(t)) m.addToMap(t, node)

  def build(clazz: PsiClass): Map = {
    if (!clazz.isValid) MixinNodes.emptyMap[T]
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
              processScala(template, ScSubstitutor.empty, map, place)
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
              processScala(template, ScSubstitutor.empty, map, place)
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
              case template: ScTemplateDefinition => processScala(template, newSubst, newMap, place)
              case syn: ScSyntheticClass =>
                //it's required to do like this to have possibility mix Synthetic types
                syn.elementScope.getCachedClass(syn.getQualifiedName)
                  .collect {
                    case template: ScTemplateDefinition => template
                  }.foreach {
                  processScala(_, newSubst, newMap, place)
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
}

object MixinNodes {
  private type SigToSuper[T] = (T, Node[T])

  class Node[T](val info: T, val substitutor: ScSubstitutor) {
    var supers: Seq[Node[T]] = Seq.empty
    var primarySuper: Option[Node[T]] = None
  }

  class Map[T](implicit strategy: SignatureStrategy[T]) {
    import strategy._

    private[Map] val implicitNames: SmartHashSet[String] = new SmartHashSet[String]
    private val publicsMap: mutable.HashMap[String, NodesMap[T]] = mutable.HashMap.empty
    private val privatesMap: mutable.HashMap[String, ArrayBuffer[SigToSuper[T]]] = mutable.HashMap.empty

    private[MixinNodes] def addToMap(key: T, node: Node[T]) {
      val name = ScalaNamesUtil.clean(elemName(key))
      if (isPrivate(key)) {
        privatesMap.getOrElseUpdate(name, ArrayBuffer.empty) += ((key, node))
      }
      else {
        val nodesMap = publicsMap.getOrElseUpdate(name, new NodesMap[T])

        nodesMap.get(key) match {
          case Some(oldNode) if isSyntheticShadedBy(node, oldNode) => //don't add synthetic if real member was already found
          case _ => nodesMap.update(key, node)
        }
      }
      if (isImplicit(key)) implicitNames.add(name)
    }

    def publicNames: collection.Set[String] = publicsMap.keySet

    def addPublicsFrom(map: Map[T]): Unit = publicsMap ++= map.publicsMap

    def nodesIterator(decodedName: String,
                      isSupers: Boolean,
                      onlyImplicit: Boolean = false): Iterator[Node[T]] = {

      if (decodedName != "") {
        val allForName =
          if (!isSupers) forName(decodedName)._1 else forName(decodedName)._2
        allForName.nodesIterator
      } else if (onlyImplicit) {
        forImplicits().iterator.map(_._2)
      } else {
        val allNodesSeq = if (!isSupers) allFirstSeq() else allSecondSeq()
        allNodesSeq.iterator.flatMap(_.nodesIterator)
      }
    }

    @volatile
    private var supersList: List[Map[T]] = List.empty
    def setSupersMap(list: List[Map[T]]) {
      for (m <- list) {
        implicitNames.addAll(m.implicitNames)
      }
      supersList = list
    }

    private val thisAndSupersMap = ContainerUtil.newConcurrentMap[String, (AllNodes[T], AllNodes[T])]()

    def forName(name: String): (AllNodes[T], AllNodes[T]) = {
      val convertedName = ScalaNamesUtil.clean(name)
      def calculate: (AllNodes[T], AllNodes[T]) = {
        val thisMap: NodesMap[T] = publicsMap.getOrElse(convertedName, NodesMap.empty[T])
        val maps: List[NodesMap[T]] = supersList.map(sup => sup.publicsMap.getOrElse(convertedName, NodesMap.empty))
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
    private var forImplicitsCache: List[SigToSuper[T]] = null

    def forImplicits(): List[SigToSuper[T]] = {
      def implicitEntry(sigToSuper: SigToSuper[T]): Option[SigToSuper[T]] = {
        val (sig, primarySuper) = sigToSuper

        val nonAbstract =
          if (isAbstract(sig) && !isAbstract(primarySuper.info)) primarySuper.info
          else sig

        if (isImplicit(nonAbstract))
          Some((nonAbstract, primarySuper))
        else None
      }

      if (forImplicitsCache != null) return forImplicitsCache

      val res = new ArrayBuffer[SigToSuper[T]]()
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

    def allFirstSeq(): Seq[AllNodes[T]] = {
      computeForAllNames()
      thisAndSupersMap.values().asScala.map(_._1).toSeq
    }

    def allSecondSeq(): Seq[AllNodes[T]] = {
      computeForAllNames()
      thisAndSupersMap.values().asScala.map(_._2).toSeq
    }

    private def toNodesSeq(seq: List[SigToSuper[T]]): NodesSeq[T] = {
      val map = new TIntObjectHashMap[List[SigToSuper[T]]]()
      for (elem <- seq) {
        val key = computeHashCode(elem._1)
        val prev = map.getOrElse(key, List.empty)
        map.put(key, elem :: prev)
      }
      new NodesSeq(map)
    }

    private class MultiMap extends mutable.HashMap[T, mutable.Set[Node[T]]] with collection.mutable.MultiMap[T, Node[T]] {
      override def elemHashCode(t : T): Int = computeHashCode(t)
      override def elemEquals(t1 : T, t2 : T): Boolean = equiv(t1, t2)
      override def makeSet = new mutable.LinkedHashSet[Node[T]]
    }

    private object MultiMap {def empty = new MultiMap}

    private def mergeSupers(maps: List[NodesMap[T]]) : MultiMap = {
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
    private def mergeWithSupers(thisMap: NodesMap[T], supersMerged: MultiMap): NodesMap[T] = {
      val primarySupers = new NodesMap[T]
      for ((key, nodes) <- supersMerged) {
        val primarySuper = nodes.find {n => !isAbstract(n.info)} match {
          case None => nodes.toList.head
          case Some(concrete) => concrete
        }
        primarySupers += ((key, primarySuper))

        def updateThisMap(): Unit = {
          nodes -= primarySuper
          primarySuper.supers = nodes.toSeq
          thisMap.update(key, primarySuper)
        }

        def updateSupersOf(node: Node[T]): Unit = {
          node.primarySuper = Some(primarySuper)
          node.supers = nodes.toSeq
        }

        thisMap.get(key) match {
          case Some(node) if isSyntheticShadedBy(node, primarySuper) => updateThisMap()
          case Some(node)                                            => updateSupersOf(node)
          case None                                                  => updateThisMap()
        }
      }
      primarySupers
    }

    def isSyntheticShadedBy(synth: Node[T], realNode: Node[T]): Boolean =
      isSynthetic(synth.info) && !isAbstract(realNode.info)

  }


  object NodesMap {
    def empty[T: SignatureStrategy]: NodesMap[T] = new NodesMap[T]()
  }

  def emptyMap[T: SignatureStrategy]: MixinNodes.Map[T] = new MixinNodes.Map[T]

  class AllNodes[T: SignatureStrategy](publics: NodesMap[T], privates: NodesSeq[T]) {

    def get(s: T): Option[Node[T]] = {
      publics.get(s) match {
        case res: Some[Node[T]] => res
        case _ => privates.get(s)
      }
    }

    def foreach(p: SigToSuper[T] => Unit) {
      publics.foreach(p)
      privates.map.flattenValues.foreach(p)
    }

    def map[R](p: SigToSuper[T] => R): Seq[R] = {
      publics.map(p).toSeq ++ privates.map.flattenValues.map(p)
    }

    def filter(p: SigToSuper[T] => Boolean): Seq[SigToSuper[T]] = {
      publics.filter(p).toSeq ++ privates.map.flattenValues.filter(p)
    }

    def withFilter(p: SigToSuper[T] => Boolean): FilterMonadic[SigToSuper[T], Seq[SigToSuper[T]]] = {
      (publics.toSeq ++ privates.map.flattenValues).withFilter(p)
    }

    def flatMap[R](p: SigToSuper[T] => Traversable[R]): Seq[R] = {
      publics.flatMap(p).toSeq ++ privates.map.flattenValues.flatMap(p)
    }

    def iterator: Iterator[SigToSuper[T]] = {
      new Iterator[SigToSuper[T]] {
        private val iter1 = publics.iterator
        private val iter2 = privates.map.flattenValues.iterator
        def hasNext: Boolean = iter1.hasNext || iter2.hasNext

        def next(): SigToSuper[T] = if (iter1.hasNext) iter1.next() else iter2.next()
      }
    }

    def nodesIterator: Iterator[Node[T]] = new Iterator[Node[T]] {
      private val iter1 = publics.valuesIterator
      private val iter2 = privates.map.flattenValues.iterator.map(_._2)

      def hasNext: Boolean = iter1.hasNext || iter2.hasNext

      def next(): Node[T] = if (iter1.hasNext) iter1.next() else iter2.next()
    }

    def fastPhysicalSignatureGet(key: T): Option[Node[T]] = {
      publics.fastPhysicalSignatureGet(key) match {
        case res: Some[Node[T]] => res
        case _ => privates.get(key)
      }
    }

    def isEmpty: Boolean = publics.isEmpty && privates.map.forEachValue(_.isEmpty)
  }

  class NodesSeq[T: SignatureStrategy](private[MixinNodes] val map: TIntObjectHashMap[List[SigToSuper[T]]]) {
    def add(s: T, node: Node[T]): Unit = {
      val key = SignatureStrategy[T].computeHashCode(s)
      val prev = map.get(key)
      map.put(key, (s, node) :: prev)
    }

    def addAll(list: List[SigToSuper[T]]): Unit = {
      list.foreach {
        case (s, node) => add(s, node)
      }
    }

    def get(s: T): Option[Node[T]] = {
      val list = map.getOrElse(SignatureStrategy[T].computeHashCode(s), Nil)
      val iterator = list.iterator
      while (iterator.hasNext) {
        val next = iterator.next()
        if (SignatureStrategy[T].same(s, next._1)) return Some(next._2)
      }
      None
    }
  }

  class NodesMap[T: SignatureStrategy] extends mutable.HashMap[T, Node[T]] {
    
    override def elemHashCode(t: T): Int = SignatureStrategy[T].computeHashCode(t)
    override def elemEquals(t1 : T, t2 : T): Boolean = SignatureStrategy[T].equiv(t1, t2)

    /**
      * Use this method if you are sure, that map contains key
      */
    def fastGet(key: T): Option[Node[T]] = {
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

    def fastPhysicalSignatureGet(key: T): Option[Node[T]] = {
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
