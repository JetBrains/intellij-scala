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
import com.intellij.psi.{PsiClass, PsiClassType}
import com.intellij.util.containers.{ContainerUtil, SmartHashSet}
import com.intellij.util.{AstLoadingFilter, SmartList}
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes.dealias
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
  type SupersMap = MixinNodes.SupersMap[T]
  type Node = MixinNodes.Node[T]

  def shouldSkip(t: T): Boolean

  def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map)

  def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, map: Map): Unit

  def processRefinement(cp: ScCompoundType, map: Map): Unit

  final def addToMap(t: T, substitutor: ScSubstitutor, m: Map): Unit =
    if (!shouldSkip(t)) m.addToMap(t, substitutor)

  def build(clazz: PsiClass): Map = {
    if (!clazz.isValid) MixinNodes.emptyMap[T]
    else {
      AstLoadingFilter.disallowTreeLoading { () =>

        val map = new Map

        addAllFrom(clazz, ScSubstitutor.empty, map)

        val superTypes = clazz match {
          case syn: ScSyntheticClass          => syn.getSuperTypes.map(_.toScType()(syn)).toSeq
          case newTd: ScNewTemplateDefinition => MixinNodes.linearization(newTd)
          case _                              => MixinNodes.linearization(clazz).drop(1)
        }
        val thisTypeSubst = clazz match {
          case td: ScTemplateDefinition => ScSubstitutor(ScThisType(td))
          case _                        => ScSubstitutor.empty
        }

        val isPredef = clazz.isInstanceOf[ScTypeDefinition] && clazz.qualifiedName == "scala.Predef"

        addSuperSignatures(superTypes, thisTypeSubst, map, isPredef)
        map
      }
    }
  }

  def build(cp: ScCompoundType, compoundThisType: Option[ScType] = None): Map = {
    val map = new Map

    processRefinement(cp, map)

    val superTypes = MixinNodes.linearization(cp)
    val thisTypeSubst = ScSubstitutor(compoundThisType.getOrElse(cp))

    addSuperSignatures(superTypes, thisTypeSubst, map)
    map
  }

  private def superTypeMap(superType: ScType, thisTypeSubst: ScSubstitutor, isPredef: Boolean): SupersMap = {
    val newMap = new SupersMap

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

          addAllFrom(superClass, newSubst, newMap)
        }
      case _ =>
        dealias(superType) match {
          case cp: ScCompoundType =>
            processRefinement(cp, newMap)
          case _ =>
        }
    }
    newMap
  }

  private def addSuperSignatures(superTypes: Seq[ScType],
                                 thisTypeSubst: ScSubstitutor,
                                 map: Map,
                                 isPredef: Boolean = false): Unit = {

    for (superType <- superTypes) {
      map.addSupersMap(superTypeMap(superType, thisTypeSubst, isPredef))
    }
  }

  private def combine(superSubst: ScSubstitutor, superClass : PsiClass): ScSubstitutor = {
    val typeParameters = superClass.getTypeParameters
    val substedTpts = typeParameters.map(tp => superSubst(TypeParameterType(tp)))
    ScSubstitutor.bind(typeParameters, substedTpts)
  }

  @tailrec
  private def addAllFrom(clazz: PsiClass, substitutor: ScSubstitutor, map: Map): Unit = {
    clazz match {
      case null                     => ()
      case syn: ScSyntheticClass    => addAllFrom(realClass(syn), substitutor, map)
      case td: ScTemplateDefinition => processScala(td, substitutor, map)
      case clazz: PsiClass          => processJava(clazz, substitutor, map)
      case _                        => ()
    }
  }

  private def realClass(syn: ScSyntheticClass): ScTemplateDefinition =
    syn.elementScope.getCachedClass(syn.getQualifiedName)
      .filterByType[ScTemplateDefinition].orNull

}

object MixinNodes {
  private type SigToNode[T] = (T, Node[T])

  class Node[T](val info: T, val substitutor: ScSubstitutor, val fromSuper: Boolean) {
    var supers: Seq[Node[T]] = Seq.empty
    var primarySuper: Option[Node[T]] = None
  }

  class Map[T](implicit strategy: SignatureStrategy[T]) {
    import strategy._

    private[Map] val implicitNames: SmartHashSet[String] = new SmartHashSet[String]
    protected val publicsMap: mutable.HashMap[String, NodesMap[T]] = mutable.HashMap.empty
    protected val privatesMap: mutable.HashMap[String, PrivateNodes[T]] = mutable.HashMap.empty

    private val thisAndSupersMap = ContainerUtil.newConcurrentMap[String, AllNodes[T]]()

    private val superMaps: ArrayBuffer[SupersMap[T]] = new ArrayBuffer(4) //looks like a reasonable default initial size

    protected lazy val allNames: Seq[String] = {
      val names = new mutable.HashSet[String]
      names ++= publicsMap.keySet
      names ++= privatesMap.keySet
      for (sup <- superMaps) {
        names ++= sup.allNames
      }
      names.toSeq
    }

    lazy val forImplicits: Seq[SigToNode[T]] = {
      val res = new ArrayBuffer[SigToNode[T]](implicitNames.size)
      val iterator = implicitNames.iterator()
      while (iterator.hasNext) {
        val thisMap = forName(iterator.next)
        res ++= thisMap.flatMap(implicitEntry)
      }
      res
    }

    protected def fromSuper: Boolean = false

    private[MixinNodes] def addToMap(key: T, substitutor: ScSubstitutor) {
      val name = elemName(key)
      val node = new Node(key, substitutor, fromSuper)
      if (isPrivate(key)) {
        privatesMap.getOrElseUpdate(name, PrivateNodes.empty).add(node)
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

      val allIterator =
        if (decodedName != "")
          forName(decodedName).nodesIterator
        else if (onlyImplicit)
          forImplicits.iterator.map(_._2)
        else
          forAllNames().iterator.flatMap(_.nodesIterator)

      if (isSupers) allIterator.flatMap(node => if (node.fromSuper) Iterator(node) else node.primarySuper.iterator)
      else allIterator
    }

    private[MixinNodes] def addSupersMap(supersMap: SupersMap[T]): Unit = {
      implicitNames.addAll(supersMap.implicitNames)
      superMaps += supersMap
    }

    def forName(name: String): AllNodes[T] = {
      val convertedName = ScalaNamesUtil.clean(name)
      def calculate: AllNodes[T] = {
        val thisMap: NodesMap[T] = publicsMap.getOrElse(convertedName, NodesMap.empty[T])
        val maps: Seq[NodesMap[T]] = superMaps.map(sup => sup.publicsMap.getOrElse(convertedName, NodesMap.empty))

        mergeWithSupers(thisMap, mergeSupers(maps))

        val supersPrivates = privatesFromSupersForName(convertedName)
        val thisPrivates = privatesMap.getOrElse(convertedName, PrivateNodes.empty)

        //to show privates from supers as inaccessible instead of unresolved
        thisPrivates.addAll(supersPrivates)

        new AllNodes(thisMap, thisPrivates)
      }
      thisAndSupersMap.atomicGetOrElseUpdate(convertedName, calculate)
    }

    def forAllNames(): Seq[AllNodes[T]] = allNames.map(forName)

    private def implicitEntry(sigToSuper: SigToNode[T]): Option[SigToNode[T]] = {
      val (sig, primarySuper) = sigToSuper

      val nonAbstract =
        if (isAbstract(sig) && !isAbstract(primarySuper.info)) primarySuper.info
        else sig

      if (isImplicit(nonAbstract))
        Some((nonAbstract, primarySuper))
      else None
    }

    private def privatesFromSupersForName(name: String): PrivateNodes[T] = {
      val result = PrivateNodes.empty
      superMaps.foreach { map =>
        result.addAll(map.privatesMap.getOrElse(name, PrivateNodes.empty))
      }
      result
    }

    def foreachThisSignature(action: T => Unit): Unit = {
      for (nodesMap <- publicsMap.values; node <- nodesMap.values) {
        action(node.info)
      }
      for (privatesSet <- privatesMap.values; node <- privatesSet.asScala) {
        action(node.info)
      }
    }

    def foreachSignature(action: T => Unit): Unit = {
      foreachThisSignature(action)
      superMaps.foreach(_.foreachThisSignature(action))
    }

    private class MultiMap extends mutable.HashMap[T, mutable.Set[Node[T]]] with collection.mutable.MultiMap[T, Node[T]] {
      override def elemHashCode(t : T): Int = computeHashCode(t)
      override def elemEquals(t1 : T, t2 : T): Boolean = equiv(t1, t2)
      override def makeSet = new mutable.LinkedHashSet[Node[T]]
    }

    private object MultiMap {def empty = new MultiMap}

    private def mergeSupers(maps: Seq[NodesMap[T]]) : MultiMap = {
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

    //adds nodes from supers to thisMap
    private def mergeWithSupers(thisMap: NodesMap[T], supersMerged: MultiMap): Unit = {
      for ((key, nodes) <- supersMerged) {

        ProgressManager.checkCanceled()

        val primarySuper = nodes.find {n => !isAbstract(n.info)} match {
          case None => nodes.head
          case Some(concrete) => concrete
        }

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
    }

    def isSyntheticShadedBy(synth: Node[T], realNode: Node[T]): Boolean =
      isSynthetic(synth.info) && !isAbstract(realNode.info)

  }

  class SupersMap[T: SignatureStrategy] extends Map[T] {
    override def fromSuper: Boolean = true
  }

  object NodesMap {
    def empty[T: SignatureStrategy]: NodesMap[T] = new NodesMap[T]()
  }

  def emptyMap[T: SignatureStrategy]: MixinNodes.Map[T] = new MixinNodes.Map[T]

  class AllNodes[T: SignatureStrategy](publics: NodesMap[T], privates: PrivateNodes[T]) {

    def get(s: T): Option[Node[T]] = {
      publics.get(s) match {
        case res: Some[Node[T]] => res
        case _ => privates.get(s)
      }
    }

    def foreach(p: SigToNode[T] => Unit) {
      publics.foreach(p)
      privates.sigToNodes.foreach(p)
    }

    def map[R](p: SigToNode[T] => R): Seq[R] = {
      publics.map(p).toSeq ++ privates.sigToNodes.map(p)
    }

    def filter(p: SigToNode[T] => Boolean): Seq[SigToNode[T]] = {
      publics.filter(p).toSeq ++ privates.sigToNodes.filter(p)
    }

    def withFilter(p: SigToNode[T] => Boolean): FilterMonadic[SigToNode[T], Seq[SigToNode[T]]] = {
      (publics.toSeq ++ privates.sigToNodes).withFilter(p)
    }

    def flatMap[R](p: SigToNode[T] => Traversable[R]): Seq[R] = {
      publics.flatMap(p).toSeq ++ privates.sigToNodes.flatMap(p)
    }

    def iterator: Iterator[SigToNode[T]] = {
      new Iterator[SigToNode[T]] {
        private val iter1 = publics.iterator
        private val iter2 = privates.sigToNodes.iterator
        def hasNext: Boolean = iter1.hasNext || iter2.hasNext

        def next(): SigToNode[T] = if (iter1.hasNext) iter1.next() else iter2.next()
      }
    }

    def nodesIterator: Iterator[Node[T]] = new Iterator[Node[T]] {
      private val iter1 = publics.valuesIterator
      private val iter2 = privates.sigToNodes.iterator.map(_._2)

      def hasNext: Boolean = iter1.hasNext || iter2.hasNext

      def next(): Node[T] = if (iter1.hasNext) iter1.next() else iter2.next()
    }

    def fastPhysicalSignatureGet(key: T): Option[Node[T]] = {
      publics.fastPhysicalSignatureGet(key) match {
        case res: Some[Node[T]] => res
        case _ => privates.get(key)
      }
    }

    def isEmpty: Boolean = publics.isEmpty && privates.isEmpty
  }

  //each set contains private members of some class with a fixed name
  //most of them are of size 0 and 1
  type PrivateNodes[T] = SmartList[Node[T]]

  object PrivateNodes {
    def empty[T: SignatureStrategy]: PrivateNodes[T] = new SmartList[Node[T]]
  }

  implicit class PrivateNodesOps[T: SignatureStrategy](set: PrivateNodes[T]) {
    def get(s: T): Option[Node[T]] = {
      val iterator = set.iterator
      while (iterator.hasNext) {
        val next = iterator.next()
        if (SignatureStrategy[T].same(s, next.info)) return Some(next)
      }
      None
    }

    def sigToNodes: Seq[(T, Node[T])] = {
      val result = new ArrayBuffer[(T, Node[T])](set.size)

      val iterator = set.iterator
      while (iterator.hasNext) {
        val node = iterator.next()
        result += ((node.info, node))
      }
      result
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
        case newTd: ScNewTemplateDefinition =>
          generalLinearization(None, newTd.superTypes)
        case _ =>
          ProgressManager.checkCanceled()
          def default =
            if (clazz.getTypeParameters.isEmpty) ScalaType.designator(clazz)
            else ScParameterizedType(ScalaType.designator(clazz),
              clazz.getTypeParameters.map(TypeParameterType(_)))

          val classType = clazz match {
            case td: ScTypeDefinition => td.`type`().getOrElse(default)
            case _ => default
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

          generalLinearization(Some(classType), supers)
      }

    }

    inner()
  }


  def linearization(compound: ScCompoundType, addTp: Boolean = false): Seq[ScType] = {
    val comps = compound.components
    val classType = if (addTp) Some(compound) else None
    generalLinearization(classType, comps)
  }


  private def generalLinearization(classType: Option[ScType], supers: Seq[ScType]): Seq[ScType] = {
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
          dealias(tp) match {
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
          dealias(tp) match {
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
    classType.foreach(add)
    buffer
  }

  private def dealias(tp: ScType) = tp.isAliasType match {
    case Some(AliasType(_: ScTypeAliasDefinition, lower, _)) => lower.getOrElse(tp)
    case _ => tp
  }
}
