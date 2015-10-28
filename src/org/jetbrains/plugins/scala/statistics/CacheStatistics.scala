package org.jetbrains.plugins.scala.statistics

import java.util.concurrent.ConcurrentHashMap

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.ContainerUtil
import org.apache.log4j.Level
import org.github.jamm.MemoryMeter

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

  val memoryMeter = new MemoryMeter()

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
    -1 //turned off counting space taken by cache, it causes errors to happen and doesn't work overall
    /*try {
      objectsToKeepTrackOfNormalReferences.map(memoryMeter.measureDeep).sum
    } catch {
      case e@(_: AssertionError | _: IllegalStateException) =>
        println(e.getMessage) //message is probably: Instrumentation is not set; Jamm must be set as -javaagent
        print("Not counting size of cache")
        -1
    }*/

  }

  override def toString: String = {
    import scala.collection.JavaConversions._
    val calcTimes: Set[Long] = calculationTimes.toSet //efficient because not conccurent

    if (calculationTimes.nonEmpty) {
      val (maxTime, minTime, averageTime) = (calcTimes.max, calcTimes.min, calcTimes.sum.toDouble / calcTimes.size)

      val timeSaved = hits * averageTime
      s"""
       |*************************************************************
       |$name
       |hits: $hits, misses: $misses
       |maxTime: $maxTime, minTime: $minTime, averageTime: $averageTime
       |time saved (hits * averageTime): $timeSaved
       |****************************
     """.stripMargin
    } else {
      s"""
        |*************************************************************
        |$name not used
        |*************************************************************
      """.stripMargin
    }
  }
}

object CacheStatistics {
  import scala.collection.JavaConverters._

  private val caches = new ConcurrentHashMap[String, CacheStatistics]()

  def printStats(): Unit = {
    val logger = Logger.getInstance(this.getClass)
    logger.setLevel(Level.INFO)
    caches.values().asScala.foreach (c => logger.info(c.toString))
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
