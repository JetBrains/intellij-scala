package org.jetbrains.plugins.scala.statistics

import java.util.concurrent.ConcurrentHashMap

import com.intellij.util.containers.ContainerUtil
import org.github.jamm.MemoryMeter
import org.jetbrains.plugins.scala.statistics.CacheStatistics.memoryMeter

import scala.collection.mutable
import scala.ref.WeakReference

/**
*  Author: Svyatoslav Ilinskiy
*  Date: 10/9/15.
*/
class CacheStatistics private(id: String, name: String) {
  @volatile
  var cachedAreaEntrances: Long = 0
  @volatile
  var cachesRecalculated: Long = 0
  val objectsToKeepTrackOf = ContainerUtil.newConcurrentSet[WeakReference[AnyRef]]
  val calculationTimes = ContainerUtil.newConcurrentSet[Long]()

  //we could ask time of entrance to measure time locality
  //also, we could find out whether multiple threads are calculating this cache at the same time
  def aboutToEnterCachedArea(): Unit = {
    cachedAreaEntrances += 1
  }

  def recalculatingCache(): Unit = {
    cachesRecalculated += 1
  }

  def reportTimeToCalculate(time: Long): Unit = {

    calculationTimes.add(time)
  }

  def hits: Long = cachedAreaEntrances - cachesRecalculated

  def misses: Long = cachesRecalculated

  def addCacheObject(obj: Any): Unit = obj match {
    case ref: AnyRef => objectsToKeepTrackOf.add(new WeakReference[AnyRef](ref))
    case _ => //it's a primitive, its size is so tiny, so let's ignore it for now
  }

  def removeCacheObject(obj: Any): Boolean = {
    import scala.collection.JavaConversions._
    var res = false
    objectsToKeepTrackOf.foreach {
      case WeakReference(el) if el.equals(obj) => res = objectsToKeepTrackOf.remove(el)
      case WeakReference(el) =>
      case t => objectsToKeepTrackOf.remove(t) //weak refernce has expired
    }
    res
  }

  def objectsToKeepTrackOfNormalReferences: mutable.Set[Any] = {
    import scala.collection.JavaConversions._
    objectsToKeepTrackOf.collect {
      case WeakReference(ref) => ref
    }
  }

  //this method may take a while time to run
  def spaceTakenByCache: Long = {
    try {
      objectsToKeepTrackOfNormalReferences.map(memoryMeter.measureDeep).sum
    } catch {
      case e@(_: AssertionError | _: IllegalStateException) =>
        println(e.getMessage) //message is probably: Instrumentation is not set; Jamm must be set as -javaagent
        print("Not counting size of cache")
        -1
    }

  }

  override def toString: String = {
    import scala.collection.JavaConversions._
    val calcTimes: Set[Long] = calculationTimes.toSet //efficient because not conccurent

    val averageTimes =
      if (calcTimes.nonEmpty) {
        s"average time to calculate: ${calcTimes.sum.toDouble / calcTimes.size}, maxTime: ${calcTimes.max}, minTime: ${calcTimes.min}"
      } else ""

    s"""
       |****************************
       |$name
       |hits: $hits, misses: $misses
       |*approximate* spaceTaken: $spaceTakenByCache
       |$averageTimes
       |****************************
     """.stripMargin
  }
}

object CacheStatistics {
  import scala.collection.JavaConverters._

  val memoryMeter = new MemoryMeter()

  private val caches = new ConcurrentHashMap[String, CacheStatistics]()

  def printStats(): Unit = {
    caches.values().asScala.foreach (c => println(c.toString))
  }

  def apply(id: String, name: String) = Option(caches.get(id)) match {
    case Some(res) => res
    case _ => synchronized {
      Option(caches.get(id)) match {
        case Some(res) => res
        case _ =>
          val res = new CacheStatistics(id, name)
          caches.put(id, res)
          res
      }
    }
  }
}
