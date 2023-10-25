package org.jetbrains.plugins.scala.caches.stats

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.plugins.scala.caches.stats.Tracer.{currentTracers, root, roundToMillis}
import org.jetbrains.plugins.scala.util.UnloadableThreadLocal

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

class Tracer private (val id: String, val name: String) {

  private val invocationCounter       = new AtomicInteger(0)
  private val actualCounter           = new AtomicInteger(0)
  private val maxCalculationTime      = new AtomicLong(0)
  private val totalCalculationTime    = new AtomicLong(0)
  private val ownCalculationTime      = new AtomicLong(0)
  private val currentCalculationStart = ThreadLocal.withInitial[java.lang.Long](() => null)
  private val lastUpdate              = ThreadLocal.withInitial[java.lang.Long](() => null)
  private val recursionDepth          = ThreadLocal.withInitial[Int](() => 0)

  private val parentCallCounters      = new MyConcurrentMap[Tracer, AtomicInteger]

  def invocation(): Unit = {
    invocationCounter.incrementAndGet()
  }

  def calculationStart(): Unit = {
    val currentTime = System.nanoTime()
    pushNested(currentTime)

    actualCounter.incrementAndGet()
    if (recursionDepth.get == 1) {
      currentCalculationStart.set(currentTime)
      lastUpdate.set(currentTime)
    }
  }

  def calculationEnd(): Unit = {
    val currentTime  = System.nanoTime()

    updateTotalTime(currentTime, isNested = false)

    if (recursionDepth.get == 1) {
      val duration = currentTime - currentCalculationStart.get()
      maxCalculationTime.updateAndGet(_ max duration)

      currentCalculationStart.set(null)
      lastUpdate.set(null)
    }

    popNested(currentTime)
  }

  private def pushNested(currentTime: Long): Unit = {
    recursionDepth.set(recursionDepth.get + 1)

    val tracers = currentTracers.value
    tracers match {
      case previous :: _ =>
        previous.updateTotalTime(currentTime, isNested = false)
        callFrom(previous)
      case _ =>
        callFrom(root)
    }
    currentTracers.value = this :: tracers
  }

  private def popNested(currentTime: Long): Unit = {
    val tail = currentTracers.value.tail
    currentTracers.value = tail

    tail match {
      case previous :: _ => previous.updateTotalTime(currentTime, isNested = true)
      case _ =>
    }

    recursionDepth.set(recursionDepth.get - 1)
  }

  private def updateTotalTime(currentTime: Long, isNested: Boolean): Unit = {
    if (recursionDepth.get == 1) {
      val delta = currentTime - lastUpdate.get

      if (!isNested)
        ownCalculationTime.addAndGet(delta)

      totalCalculationTime.addAndGet(delta)
      lastUpdate.set(currentTime)
    }
  }

  private def callFrom(other: Tracer): Unit = {
    val intRef = parentCallCounters.computeIfAbsent(other, _ => new AtomicInteger(0))
    intRef.addAndGet(1)
  }

  private def fromCacheCount: Int = invocationCounter.get - actualCount
  private def actualCount   : Int = actualCounter.get
  private def maxTime       : Int = roundToMillis(maxCalculationTime.get)
  private def ownTime       : Int = roundToMillis(ownCalculationTime.get)
  private def totalTime     : Int = roundToMillis(totalCalculationTime.get)

  private def parentCalls   : java.util.List[(String, Int)] =
    parentCallCounters.map((tr, ref) => (tr.name, ref.get))

  def getCurrentData: TracerData =
    TracerData(id, name, fromCacheCount, actualCount, maxTime, ownTime, totalTime, parentCalls)
}

object Tracer {

  private object NoOp extends Tracer("NoOpTracer$$", "NoOpTracer") {
    override def calculationStart(): Unit = ()
    override def calculationEnd(): Unit = ()

    override def invocation(): Unit = ()

    override def getCurrentData: Nothing = ???
  }

  private val tracingProperty = System.getProperty("internal.profiler.tracing") == "true"

  final val BEFORE_CACHE_READ: Int = 0

  val isAvailable: Boolean = {
    tracingProperty || ApplicationManager.getApplication.isInternal
  }

  private var _enabled: Boolean = tracingProperty

  def isEnabled: Boolean = _enabled

  def setEnabled(v: Boolean): Unit = {
    _enabled = v
  }

  private val tracersMap =
    new MyConcurrentMap[String, Tracer]()

  def apply(id: String, name: String): Tracer =
    if (isEnabled) tracersMap.computeIfAbsent(id, new Tracer(_, name))
    else NoOp

  def clearAll(): Unit =
    tracersMap.clear()

  def getCurrentData: java.util.List[TracerData] =
    tracersMap.map((_, v) => v.getCurrentData)

  private val root = new Tracer("root-tracer-id$$", "<root>")

  private def roundToMillis(nanos: Long): Int = Math.round(nanos.toDouble / (1000 * 1000)).toInt

  private val currentTracers: UnloadableThreadLocal[List[Tracer]] = new UnloadableThreadLocal(Nil)

}