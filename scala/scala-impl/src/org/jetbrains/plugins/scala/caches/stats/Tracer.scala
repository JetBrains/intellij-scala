package org.jetbrains.plugins.scala.caches.stats

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong, AtomicReference}

import com.intellij.openapi.application.ApplicationManager

import scala.collection.JavaConverters._

trait Tracer {
  def id: String
  def name: String

  def invocation()      : Unit
  def calculationStart(): Unit
  def calculationEnd()  : Unit

  protected def fromCacheCount: Int
  protected def actualCount   : Int
  protected def maxTime       : Int
  protected def ownTime       : Int
  protected def totalTime     : Int

  //name to count
  protected def parentCalls   : java.util.List[(String, Int)]

  def data: TracerData =
    TracerData(id, name, fromCacheCount, actualCount, maxTime, ownTime, totalTime, parentCalls)
}

object Tracer {
  private val tracingProperty = System.getProperty("internal.profiler.tracing") == "true"

  val isAvailable: Boolean = {
    tracingProperty || ApplicationManager.getApplication.isInternal
  }

  private var _enabled: Boolean = tracingProperty

  def isEnabled: Boolean = _enabled

  def setEnabled(v: Boolean): Unit = {
    _enabled = v
  }

  def apply(id: String, name: String): Tracer =
    if (isEnabled) TracerImpl.getOrCreate(id, name)
    else NoOpTracer

  def clearAll(): Unit = TracerImpl.clearAll()

  def getCurrentData: java.util.List[TracerData] = TracerImpl.currentData

  private object NoOpTracer extends Tracer {
    override def id: String = "$$NoOpTracer$$"
    override def name: String = "No cache stats"

    override def invocation()      : Unit = ()
    override def calculationStart(): Unit = ()
    override def calculationEnd()  : Unit = ()

    override protected def fromCacheCount: Int = 0
    override protected def actualCount   : Int = 0
    override protected def maxTime       : Int = 0
    override protected def ownTime       : Int = 0
    override protected def totalTime     : Int = 0

    override protected def parentCalls: java.util.List[(String, Int)] = java.util.Collections.emptyList()
  }

  private val root = new TracerImpl("root-tracer-id$$", "<root>")

  private class TracerImpl(val id: String, val name: String) extends Tracer {
    private val invocationCounter       = new AtomicInteger(0)
    private val actualCounter           = new AtomicInteger(0)
    private val maxCalculationTime      = new AtomicLong(0)
    private val totalCalculationTime    = new AtomicLong(0)
    private val ownCalculationTime      = new AtomicLong(0)
    private val currentCalculationStart = ThreadLocal.withInitial[java.lang.Long](() => null)
    private val lastUpdate              = ThreadLocal.withInitial[java.lang.Long](() => null)
    private val recursionDepth          = ThreadLocal.withInitial[Int](() => 0)

    private val parentCallCounters      = new MyConcurrentMap[TracerImpl, AtomicInteger]

    override def invocation(): Unit = {
      invocationCounter.incrementAndGet()
    }

    override def calculationStart(): Unit = {
      val currentTime = System.nanoTime()
      pushNested(currentTime)

      actualCounter.incrementAndGet()
      if (recursionDepth.get == 1) {
        currentCalculationStart.set(currentTime)
        lastUpdate.set(currentTime)
      }
    }

    override def calculationEnd(): Unit = {
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

      currentTracers.get() match {
        case previous :: _ =>
          previous.updateTotalTime(currentTime, isNested = false)
          callFrom(previous)
        case _ =>
          callFrom(root)
      }
      currentTracers.set(this :: currentTracers.get())
    }

    private def popNested(currentTime: Long): Unit = {
      currentTracers.set(currentTracers.get().tail)

      currentTracers.get() match {
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

    private def callFrom(other: TracerImpl): Unit = {
      val intRef = parentCallCounters.computeIfAbsent(other, _ => new AtomicInteger(0))
      intRef.addAndGet(1)
    }

    override protected def fromCacheCount: Int = invocationCounter.get - actualCount
    override protected def actualCount   : Int = actualCounter.get
    override protected def maxTime       : Int = roundToMillis(maxCalculationTime.get)
    override protected def ownTime       : Int = roundToMillis(ownCalculationTime.get)
    override protected def totalTime     : Int = roundToMillis(totalCalculationTime.get)

    override protected def parentCalls   : java.util.List[(String, Int)] =
      parentCallCounters.map((tr, ref) => (tr.name, ref.get))
  }

  private def roundToMillis(nanos: Long): Int = Math.round(nanos.toDouble / (1000 * 1000)).toInt

  private val currentTracers: ThreadLocal[List[TracerImpl]] = ThreadLocal.withInitial(() => Nil)

  private object TracerImpl {

    private val tracersMap =
      new MyConcurrentMap[String, Tracer]()

    def getOrCreate(id: String, name: String): Tracer = {
      tracersMap.computeIfAbsent(id, new TracerImpl(_, name))
    }

    def currentData: java.util.List[TracerData] =
      tracersMap.values.asScala.toSeq.map(_.data).asJava

    def clearAll(): Unit = tracersMap.clear()
  }

  private class MyConcurrentMap[K, V >: Null] {
    private val emptyMap = java.util.Collections.emptyMap[K, V]()

    private val ref: AtomicReference[java.util.Map[K, V]] = new AtomicReference(emptyMap)

    def computeIfAbsent(k: K, v: K => V): V = {
      do {
        val prev = ref.get()
        prev.get(k) match {
          case null =>
            val newValue = v(k)
            val newMap = add(prev, k, newValue)
            if (ref.compareAndSet(prev, newMap))
              return newValue
          case v =>
            return v
        }
      } while (true)
      //will be never reached
      null
    }

    def clear(): Unit = ref.set(emptyMap)

    def values: java.util.Collection[V] = ref.get.values()

    def map[T](f: (K, V) => T): java.util.List[T] = {
      val map = ref.get()
      val result = new java.util.ArrayList[T]
      map.forEach((k, v) => result.add(f(k, v)))
      result
    }

    private def add(oldMap: java.util.Map[K, V], key: K, value: V): java.util.Map[K, V] = {
      val newMap = new java.util.HashMap[K, V](oldMap)
      newMap.put(key, value)
      java.util.Collections.unmodifiableMap(newMap)
    }
  }

}