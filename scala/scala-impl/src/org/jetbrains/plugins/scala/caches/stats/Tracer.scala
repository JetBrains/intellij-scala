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
  protected def ownTime       : Int
  protected def totalTime     : Int

  def data: TracerData =
    TracerData(id, name, fromCacheCount, actualCount, maxTime, ownTime, totalTime)
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
    override protected def ownTime    : Int = 0
    override protected def totalTime  : Int = 0
  }

  private class TracerImpl(val id: String, val name: String) extends Tracer {
    private val invocationCounter       = new AtomicInteger(0)
    private val actualCounter           = new AtomicInteger(0)
    private val maxCalculationTime      = new AtomicInteger(0)
    private val totalCalculationTime    = new AtomicInteger(0)
    private val ownCalculationTime      = new AtomicInteger(0)
    private val currentCalculationStart = ThreadLocal.withInitial[java.lang.Long](() => null)
    private val lastUpdate              = ThreadLocal.withInitial[java.lang.Long](() => null)
    private val recursionDepth          = ThreadLocal.withInitial[Int](() => 0)

    override def invocation(): Unit = {
      invocationCounter.incrementAndGet()
    }

    override def calculationStart(): Unit = {
      val currentTime = System.currentTimeMillis()
      pushNested(currentTime)

      actualCounter.incrementAndGet()
      if (recursionDepth.get == 1) {
        currentCalculationStart.set(currentTime)
        lastUpdate.set(currentTime)
      }
    }

    override def calculationEnd(): Unit = {
      val currentTime  = System.currentTimeMillis()

      updateTotalTime(currentTime, isNested = false)

      if (recursionDepth.get == 1) {
        val duration = (currentTime - currentCalculationStart.get()).toInt
        currentCalculationStart.set(null)
        maxCalculationTime.updateAndGet(_ max duration)
      }

      popNested(currentTime)
    }

    private def pushNested(currentTime: Long): Unit = {
      recursionDepth.set(recursionDepth.get + 1)

      currentTracers.get() match {
        case previous :: _ => previous.updateTotalTime(currentTime, isNested = false)
        case _ =>
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
        val delta = (currentTime - lastUpdate.get).toInt

        if (!isNested)
          ownCalculationTime.addAndGet(delta)

        totalCalculationTime.addAndGet(delta)
        lastUpdate.set(currentTime)
      }
    }

    override protected def fromCacheCount: Int = invocationCounter.get - actualCount
    override protected def actualCount   : Int = actualCounter.get
    override protected def maxTime       : Int = maxCalculationTime.get
    override protected def ownTime       : Int = ownCalculationTime.get
    override protected def totalTime     : Int = totalCalculationTime.get
  }

  private val currentTracers: ThreadLocal[List[TracerImpl]] = ThreadLocal.withInitial(() => Nil)

  private object TracerImpl {

    private val tracersMap =
      new ConcurrentHashMap[String, Tracer]()

    def getOrCreate(id: String, name: String): Tracer = {
      tracersMap.computeIfAbsent(id, new TracerImpl(_, name))
    }

    def currentData: java.util.List[TracerData] =
      tracersMap.values().asScala.toSeq.map(_.data).asJava

    def clearAll(): Unit = tracersMap.clear()
  }

}