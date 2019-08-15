package org.jetbrains.plugins.scala.caches.stats

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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
  protected def totalTime     : Int

  def stats: TracerData =
    TracerData(id, name, fromCacheCount, actualCount, maxTime, totalTime)
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

    override protected def fromCacheCount  : Int = 0
    override protected def actualCount: Int = 0
    override protected def maxTime    : Int = 0
    override protected def totalTime  : Int = 0
  }

  private class TracerImpl(val id: String, val name: String) extends Tracer {
    private val invocationCounter       = new AtomicInteger(0)
    private val actualCounter           = new AtomicInteger(0)
    private val maxCalculationTime      = new AtomicInteger(0)
    private val totalCalculationTime    = new AtomicInteger(0)
    private val currentCalculationStart = ThreadLocal.withInitial[java.lang.Long](() => null)
    private val recursionDepth          = ThreadLocal.withInitial[Int](() => 0)

    override def invocation(): Unit = {
      invocationCounter.incrementAndGet()
    }

    override def calculationStart(): Unit = {
      actualCounter.incrementAndGet()
      if (recursionDepth.get == 0) {
        currentCalculationStart.set(System.currentTimeMillis())
      }
      recursionDepth.set(recursionDepth.get + 1)
    }

    override def calculationEnd(): Unit = {
      recursionDepth.set(recursionDepth.get - 1)
      if (recursionDepth.get == 0) {
        val start = currentCalculationStart.get()
        val duration = (System.currentTimeMillis() - start).toInt

        val max = maxCalculationTime.get() max duration
        maxCalculationTime.set(max)

        totalCalculationTime.addAndGet(duration)
        currentCalculationStart.set(null)
      }
    }

    override protected def fromCacheCount: Int = invocationCounter.get - actualCount
    override protected def actualCount   : Int = actualCounter.get
    override protected def maxTime       : Int = maxCalculationTime.get
    override protected def totalTime     : Int = totalCalculationTime.get
  }

  private object TracerImpl {

    private val tracersMap =
      new ConcurrentHashMap[String, Tracer]()

    def getOrCreate(id: String, name: String): Tracer = {
      tracersMap.computeIfAbsent(id, new TracerImpl(_, name))
    }

    def currentData: java.util.List[TracerData] =
      tracersMap.values().asScala.toSeq.map(_.stats).asJava

    def clearAll(): Unit = tracersMap.clear()
  }

}