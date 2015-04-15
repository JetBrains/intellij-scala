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
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiClass, PsiClassType, PsiElement}
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}

abstract class MixinNodes {
  type T
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

  class Map extends mutable.HashMap[String, ArrayBuffer[(T, Node)]] {
    private[Map] val implicitNames: mutable.HashSet[String] = new mutable.HashSet[String]
    private val privatesMap: mutable.HashMap[String, ArrayBuffer[(T, Node)]] = mutable.HashMap.empty
    def addToMap(key: T, node: Node) {
      val name = ScalaPsiUtil.convertMemberName(elemName(key))
      (if (!isPrivate(key)) this else privatesMap).
        getOrElseUpdate(name, new ArrayBuffer) += ((key, node))
      if (isImplicit(key)) implicitNames.add(name)
    }

    @volatile
    private var supersList: List[Map] = List.empty
    def setSupersMap(list: List[Map]) {
      for (m <- list) {
        implicitNames ++= m.implicitNames
      }
      supersList = list
    }

    private val calculatedNames: mutable.HashSet[String] = new mutable.HashSet
    private val calculated: mutable.HashMap[String, AllNodes] = new mutable.HashMap
    private val calculatedSupers: mutable.HashMap[String, AllNodes] = new mutable.HashMap

    def forName(name: String): (AllNodes, AllNodes) = {
      val convertedName = ScalaPsiUtil.convertMemberName(name)
      synchronized {
        if (calculatedNames.contains(convertedName)) {
          return (calculated(convertedName), calculatedSupers(convertedName))
        }
      }
      val thisMap: NodesMap = toNodesMap(getOrElse(convertedName, new ArrayBuffer))
      val maps: List[NodesMap] = supersList.map(sup => toNodesMap(sup.getOrElse(convertedName, new ArrayBuffer)))
      val supers = mergeWithSupers(thisMap, mergeSupers(maps))
      val list = supersList.map(_.privatesMap.getOrElse(convertedName, new ArrayBuffer[(T, Node)])).flatten
      val supersPrivates = toNodesSeq(list)
      val thisPrivates = toNodesSeq(privatesMap.getOrElse(convertedName, new ArrayBuffer[(T, Node)]).toList ::: list)
      val thisAllNodes = new AllNodes(thisMap, thisPrivates)
      val supersAllNodes = new AllNodes(supers, supersPrivates)
      synchronized {
        calculatedNames.add(convertedName)
        calculated.+=((convertedName, thisAllNodes))
        calculatedSupers.+=((convertedName, supersAllNodes))
      }
      (thisAllNodes, supersAllNodes)
    }

    @volatile
    private var forImplicitsCache: List[(T, Node)] = null
    def forImplicits(): List[(T, Node)] = {
      if (forImplicitsCache != null) return forImplicitsCache
      val res = new ArrayBuffer[(T, Node)]()
      for (name <- implicitNames) {
        val map = forName(name)._1
        for (elem <- map) {
          if (isImplicit(elem._1)) res += elem
        }
      }
      forImplicitsCache = res.toList
      forImplicitsCache
    }
    
    def allNames(): mutable.Set[String] = {
      val names = new mutable.HashSet[String]
      names ++= keySet
      names ++= privatesMap.keySet
      for (sup <- supersList) {
        names ++= sup.keySet
        names ++= sup.privatesMap.keySet
      }
      names
    }
    
    private def forAll(): (mutable.HashMap[String, AllNodes], mutable.HashMap[String, AllNodes]) = {
      for (name <- allNames()) forName(name)
      synchronized {
        (calculated, calculatedSupers)
      }
    }

    def allFirstSeq(): Seq[AllNodes] = {
      forAll()._1.toSeq.map(_._2)
    }

    def allSecondSeq(): Seq[AllNodes] = {
      forAll()._1.toSeq.map(_._2)
    }
    
    private def toNodesSeq(seq: List[(T, Node)]): NodesSeq = {
      val map = new mutable.HashMap[Int, List[(T, Node)]]
      for (elem <- seq) {
        val key = computeHashCode(elem._1)
        val prev = map.getOrElse(key, List.empty)
        map.update(key, elem :: prev)
      }
      new NodesSeq(map)
    }
    
    private def toNodesMap(buf: ArrayBuffer[(T, Node)]): NodesMap = {
      val res = new NodesMap
      res ++= buf
      res
    }

    private class MultiMap extends mutable.HashMap[T, mutable.Set[Node]] with collection.mutable.MultiMap[T, Node] {
      override def elemHashCode(t : T) = computeHashCode(t)
      override def elemEquals(t1 : T, t2 : T) = equiv(t1, t2)
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
          case None => nodes.toList(0)
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
    
    def foreach(p: ((T, Node)) => Unit) {
      publics.foreach(p)
      privates.map.values.flatten.foreach(p)
    }
    
    def map[R](p: ((T, Node)) => R): Seq[R] = {
      publics.map(p).toSeq ++ privates.map.values.flatten.map(p)
    }

    def filter(p: ((T, Node)) => Boolean): Seq[(T, Node)] = {
      publics.filter(p).toSeq ++ privates.map.values.flatten.filter(p)
    }

    def withFilter(p: ((T, Node)) => Boolean) = {
      (publics.toSeq ++ privates.map.values.flatten).withFilter(p)
    }

    def flatMap[R](p: ((T, Node)) => Traversable[R]): Seq[R] = {
      publics.flatMap(p).toSeq ++ privates.map.values.flatten.flatMap(p)
    }
    
    def iterator: Iterator[(T, Node)] = {
      new Iterator[(T, Node)] {
        private val iter1 = publics.iterator
        private val iter2 = privates.map.values.flatten.iterator
        def hasNext: Boolean = iter1.hasNext || iter2.hasNext

        def next(): (T, Node) = if (iter1.hasNext) iter1.next() else iter2.next()
      }
    }

    def fastPhysicalSignatureGet(key: T): Option[Node] = {
      publics.fastPhysicalSignatureGet(key) match {
        case res: Some[Node] => res
        case _ => privates.get(key)
      }
    }
    
    def isEmpty: Boolean = publics.isEmpty && privates.map.values.forall(_.isEmpty)
  }
  
  class NodesSeq(private[MixinNodes] val map: mutable.HashMap[Int, List[(T, Node)]]) {
    def get(s: T): Option[Node] = {
      val list = map.getOrElse(computeHashCode(s), Nil)
      val iterator = list.iterator
      while (iterator.hasNext) {
        val next = iterator.next()
        if (same(s, next._1)) return Some(next._2)
      }
      None
    }

    def fastPhysicalSignatureGet(key: T): Option[Node] = {
      val list = map.getOrElse(computeHashCode(key), List.empty)
      list match {
        case Nil => None
        case x :: Nil => Some(x._2)
        case e =>
          val iterator = e.iterator
          while (iterator.hasNext) {
            val next = iterator.next()
            if (same(key, next._1)) return Some(next._2)
          }
          None
      }
    }
  }

  class NodesMap extends mutable.HashMap[T, Node] {
    override def elemHashCode(t: T) = computeHashCode(t)
    override def elemEquals(t1 : T, t2 : T) = equiv(t1, t2)

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

  def build(clazz: PsiClass): Map = build(ScType.designator(clazz))

  def build(tp: ScType, compoundThisType: Option[ScType] = None): Map = {
    var isPredef = false
    var place: Option[PsiElement] = None
    val map = new Map
    val superTypesBuff = new ListBuffer[Map]
    val (superTypes, subst, thisTypeSubst): (Seq[ScType], ScSubstitutor, ScSubstitutor) = tp match {
      case cp: ScCompoundType =>
        processRefinement(cp, map, place)
        val thisTypeSubst = compoundThisType match {
          case Some(_) => new ScSubstitutor(Map.empty, Map.empty, compoundThisType)
          case _ => new ScSubstitutor(Predef.Map.empty, Predef.Map.empty, Some(tp))
        }
        (MixinNodes.linearization(cp), ScSubstitutor.empty, thisTypeSubst)
      case _ =>
        val clazz = tp match {
          case ScDesignatorType(clazz: PsiClass) => clazz
          case ScProjectionType(_, clazz: PsiClass, _) => clazz
          case _ => null
        }
        if (clazz == null) (Seq.empty, ScSubstitutor.empty, ScSubstitutor.empty)
        else
          clazz match {
            case template: ScTypeDefinition =>
              if (template.qualifiedName == "scala.Predef") isPredef = true
              place = Option(template.extendsBlock)
              processScala(template, ScSubstitutor.empty, map, place, base = true)
              val lin = MixinNodes.linearization(template)
              var zSubst = new ScSubstitutor(Map.empty, Map.empty, Some(ScThisType(template)))
              var placer = template.getContext
              while (placer != null) {
                placer match {
                  case t: ScTemplateDefinition => zSubst = zSubst.followed(
                    new ScSubstitutor(Map.empty, Map.empty, Some(ScThisType(t)))
                  )
                  case _ =>
                }
                placer = placer.getContext
              }
              (if (!lin.isEmpty) lin.tail else lin, Bounds.putAliases(template, ScSubstitutor.empty), zSubst)
            case template: ScTemplateDefinition =>
              place = Option(template.asInstanceOf[ScalaStubBasedElementImpl[_]].getLastChildStub)
              processScala(template, ScSubstitutor.empty, map, place, base = true)
              var zSubst = new ScSubstitutor(Map.empty, Map.empty, Some(ScThisType(template)))
              var placer = template.getContext
              while (placer != null) {
                placer match {
                  case t: ScTemplateDefinition => zSubst = zSubst.followed(
                    new ScSubstitutor(Map.empty, Map.empty, Some(ScThisType(t)))
                  )
                  case _ =>
                }
                placer = placer.getContext
              }
              (MixinNodes.linearization(template),
                Bounds.putAliases(template, ScSubstitutor.empty), zSubst)
            case syn: ScSyntheticClass =>
              (syn.getSuperTypes.map{psiType => ScType.create(psiType, syn.getProject)} : Seq[ScType],
                ScSubstitutor.empty, ScSubstitutor.empty)
            case clazz: PsiClass =>
              place = Option(clazz.getLastChild)
              processJava(clazz, ScSubstitutor.empty, map, place)
              val lin = MixinNodes.linearization(clazz)
              (if (!lin.isEmpty) lin.tail else lin,
                ScSubstitutor.empty, ScSubstitutor.empty)
            case _ =>
              (Seq.empty, ScSubstitutor.empty, ScSubstitutor.empty)
          }
    }
    val iter = superTypes.iterator
    while (iter.hasNext) {
      val superType = iter.next()
      ScType.extractClassType(superType, place.map(_.getProject)) match {
        case Some((superClass, s)) =>
          // Do not include scala.ScalaObject to Predef's base types to prevent SOE
          if (!(superClass.qualifiedName == "scala.ScalaObject" && isPredef)) {
            val dependentSubst = superType match {
              case p@ScProjectionType(proj, eem, _) => new ScSubstitutor(proj).followed(p.actualSubst)
              case ScParameterizedType(p@ScProjectionType(proj, _, _), _) => new ScSubstitutor(proj).followed(p.actualSubst)
              case _ => ScSubstitutor.empty
            }
            val newSubst = combine(s, subst, superClass).followed(thisTypeSubst).followed(dependentSubst)
            val newMap = new Map
            superClass match {
              case template: ScTemplateDefinition => processScala(template, newSubst, newMap, place, base = false)
              case syn: ScSyntheticClass =>
                //it's required to do like this to have possibility mix Synthetic types
                val clazz = ScalaPsiManager.instance(syn.getProject).getCachedClass(syn.getQualifiedName,
                  GlobalSearchScope.allScope(syn.getProject), ScalaPsiManager.ClassCategory.TYPE
                )
                clazz match {
                  case template: ScTemplateDefinition => processScala(template, newSubst, newMap, place, base = false)
                  case _ => //do nothing
                }
              case _ => processJava(superClass, newSubst, newMap, place)
            }
            superTypesBuff += newMap
          }
        case _ =>
      }
      (superType.isAliasType match {
        case Some(AliasType(td: ScTypeAliasDefinition, lower, _)) => lower.getOrElse(superType)
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

  def combine(superSubst : ScSubstitutor, derived : ScSubstitutor, superClass : PsiClass) = {
    var res : ScSubstitutor = ScSubstitutor.empty
    for (tp <- superClass.getTypeParameters) {
      res = res bindT ((tp.name, ScalaPsiUtil.getPsiElementId(tp)),
        derived.subst(superSubst.subst(ScalaPsiManager.typeVariable(tp))))
    }
    superClass match {
      case td : ScTypeDefinition =>
        var aliasesMap = res.aliasesMap
        for (alias <- td.aliases) {
          derived.aliasesMap.get(alias.name) match {
            case Some(t) => aliasesMap = aliasesMap + ((alias.name, t))
            case None =>
          }
        }
        res = new ScSubstitutor(res.tvMap, aliasesMap, None)
      case _ => ()
    }
    res
  }

  def processJava(clazz: PsiClass, subst: ScSubstitutor, map: Map, place: Option[PsiElement])
  def processScala(template: ScTemplateDefinition, subst: ScSubstitutor, map: Map,
                   place: Option[PsiElement], base: Boolean)
  def processRefinement(cp: ScCompoundType, map: Map, place: Option[PsiElement])
}

object MixinNodes {
  def linearization(clazz: PsiClass): Seq[ScType] = {
    clazz match {
      case obj: ScObject if obj.isPackageObject && obj.qualifiedName == "scala" =>
        return Seq(ScType.designator(obj))
      case _ =>
    }

    CachesUtil.getWithRecursionPreventingWithRollback(clazz, CachesUtil.LINEARIZATION_KEY,
    new CachesUtil.MyOptionalProvider(clazz, (clazz: PsiClass) => linearizationInner(clazz))
      (ScalaPsiUtil.getDependentItem(clazz)), Seq.empty)
  }


  def linearization(compound: ScCompoundType, addTp: Boolean = false): Seq[ScType] = {
    val comps = compound.components

    generalLinearization(None, compound, addTp = addTp, supers = comps)
  }

  private def linearizationInner(clazz: PsiClass): Seq[ScType] = {
    ProgressManager.checkCanceled()
    val tp = {
      def default =
        if (clazz.getTypeParameters.length == 0) ScType.designator(clazz)
        else ScParameterizedType(ScType.designator(clazz), clazz.
          getTypeParameters.map(tp => ScalaPsiManager.instance(clazz.getProject).typeVariable(tp)))
      clazz match {
        case td: ScTypeDefinition => td.getType(TypingContext.empty).getOrElse(default)
        case _ => default
      }
    }
    val supers: Seq[ScType] = {
      clazz match {
        case td: ScTemplateDefinition => td.superTypes
        case clazz: PsiClass => clazz.getSuperTypes.map {
          case ctp: PsiClassType =>
            val cl = ctp.resolve()
            if (cl != null && cl.qualifiedName == "java.lang.Object") ScDesignatorType(cl)
            else ScType.create(ctp, clazz.getProject)
          case ctp => ScType.create(ctp, clazz.getProject)
        }.toSeq
      }
    }

    generalLinearization(Some(clazz.getProject), tp, addTp = true, supers = supers)
  }
  
  private def generalLinearization(project: Option[Project], tp: ScType, addTp: Boolean, supers: Seq[ScType]): Seq[ScType] = {
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
      ScType.extractClass(tp, project) match {
        case Some(clazz) if clazz.qualifiedName != null && !set.contains(classString(clazz)) =>
          tp +=: buffer
          set += classString(clazz)
        case Some(clazz) if clazz.getTypeParameters.length != 0 =>
          val i = buffer.indexWhere(newTp => {
            ScType.extractClass(newTp, Some(clazz.getProject)) match {
              case Some(newClazz) if ScEquivalenceUtil.areClassesEquivalent(newClazz, clazz) => true
              case _ => false
            }
          })
          if (i != -1) {
            val newTp = buffer.apply(i)
            if (tp.conforms(newTp)) buffer.update(i, tp)
          }
        case _ =>
          (tp.isAliasType match {
            case Some(AliasType(td: ScTypeAliasDefinition, lower, _)) => lower.getOrElse(tp)
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
      def updateTp(tp: ScType): ScType = {
        tp.isAliasType match {
          case Some(AliasType(_, _, Success(upper, _))) => updateTp(upper)
          case _ =>
            tp match {
              case ex: ScExistentialType => ex.skolem
              case _ => tp
            }
        }
      }
      tp = updateTp(tp)
      ScType.extractClassType(tp) match {
        case Some((clazz, subst)) =>
          val lin = linearization(clazz)
          val newIterator = lin.reverseIterator
          while (newIterator.hasNext) {
            val tp = newIterator.next()
            add(subst.subst(tp))
          }
        case _ =>
          (tp.isAliasType match {
            case Some(AliasType(td: ScTypeAliasDefinition, lower, _)) => lower.getOrElse(tp)
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
    buffer.toSeq
  }
}
