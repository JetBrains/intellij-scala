package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import com.intellij.util.containers.SmartHashSet
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes.{AllNodes, Node}
import org.jetbrains.plugins.scala.lang.psi.types.Signature
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable.{ArraySeq, Seq}
import scala.jdk.CollectionConverters.IteratorHasAsScala

trait MixinNodesBase[T <: Signature] {
  type Map = MixinNodesBase.Map[T]
}

object MixinNodesBase {
  trait Map[T <: Signature] extends SignatureSink[T] {
    def allNodes: Iterator[Node[T]] = allNames.iterator().asScala.map(forName).flatMap(_.nodesIterator)

    lazy val implicitNodes: Seq[Node[T]] = {
      val builder = ArraySeq.newBuilder[Node[T]]
      builder.sizeHint(implicitNames.size)
      val iterator = implicitNames.iterator()
      while (iterator.hasNext) {
        val thisMap = forName(iterator.next)
        thisMap.nodesIterator.foreach { node =>
          if (node.info.isImplicit || node.info.isExtensionMethod) {
            builder += node
          }
        }
      }
      builder.result()
    }

    def nodesIterator(
      decodedName: String,
      isSupers: Boolean,
      onlyImplicit: Boolean = false
    ): Iterator[Node[T]] = {

      val allIterator =
        if (decodedName != "") forName(decodedName).nodesIterator
        else if (onlyImplicit) implicitNodes.iterator
        else allNodes

      if (isSupers) allIterator.flatMap(node => if (node.fromSuper) Iterator(node) else node.primarySuper.iterator)
      else allIterator
    }

    def allSignatures: Iterator[T] = allNodes.map(_.info)

    def intersect(other: Map[T]): Map[T] = new IntersectionMap(this, other)

    protected val forNameCache = new ConcurrentHashMap[String, AllNodes[T]]()

    def forName(name: String): AllNodes[T]

    def allNames: java.util.HashSet[String]

    def implicitNames: SmartHashSet[String]
  }

  class IntersectionMap[T <: Signature](lhsMap: Map[T], rhsMap: Map[T]) extends Map[T] {
    override val allNames: java.util.HashSet[String] =
      new java.util.HashSet[String]() {
        addAll(lhsMap.allNames)
        addAll(rhsMap.allNames)
      }

    override val implicitNames: SmartHashSet[String] = {
      val names = new SmartHashSet[String]()
      names.addAll(lhsMap.implicitNames)
      names.addAll(rhsMap.implicitNames)
      names
    }

    //getOrElseUpdate in JConcurrentMapWrapper is not atomic!
    private def atomicGetOrElseUpdate[K, V](map: java.util.concurrent.ConcurrentHashMap[K, V], key: K, update: => V): V = {
      Option(map.get(key)) match {
        case Some(v) => v
        case None =>
          val newValue = update
          val race = map.putIfAbsent(key, newValue)

          if (race != null) race
          else newValue
      }
    }


    override def forName(name: String): AllNodes[T] = {
      val cleanName = ScalaNamesUtil.clean(name)
      val fromLhs = lhsMap.forName(name)
      val fromRhs = rhsMap.forName(name)
      atomicGetOrElseUpdate(forNameCache, cleanName, fromLhs.merge(fromRhs))
    }

    override def put(signature: T): Unit = ()
  }
}
