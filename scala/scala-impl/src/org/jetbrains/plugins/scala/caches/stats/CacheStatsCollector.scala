package org.jetbrains.plugins.scala.caches.stats

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.SystemProperties

import scala.collection.JavaConverters._

trait CacheStatsCollector {
  def cacheName: String

  def invocation()      : Unit
  def calculationStart(): Unit
  def calculationEnd()  : Unit

  protected def cacheHits  : Int
  protected def cacheMisses: Int
  protected def maxTime    : Int
  protected def totalTime  : Int

  def stats: CacheStats =
    CacheStats(cacheName, cacheHits, cacheMisses, maxTime, totalTime)
}

object CacheStatsCollector {
  private val tracingProperty = SystemProperties.is("scala.plugin.caches.tracing")

  val isAvailable: Boolean = {
    tracingProperty || ApplicationManager.getApplication.isInternal
  }

  private var _enabled: Boolean = tracingProperty

  def isEnabled: Boolean = _enabled

  def setEnabled(v: Boolean): Unit = {
    _enabled = v
    if (!v) {
      CollectorImpl.clearAll()
    }
  }

  def apply(id: String, name: String): CacheStatsCollector =
    if (isEnabled) CollectorImpl.getOrCreate(id, name)
    else NoOpCollector

  def printReport(): Unit = {
    if (isEnabled) {
      CollectorImpl.printReport()
    }
  }

  private object NoOpCollector extends CacheStatsCollector {
    override def cacheName: String = "No cache stats"

    override def invocation()      : Unit = ()
    override def calculationStart(): Unit = ()
    override def calculationEnd()  : Unit = ()

    override protected def cacheHits  : Int = 0
    override protected def cacheMisses: Int = 0
    override protected def maxTime    : Int = 0
    override protected def totalTime  : Int = 0
  }

  private class CollectorImpl(val cacheId: String, val cacheName: String) extends CacheStatsCollector {
    private val invocationCounter       = new AtomicInteger(0)
    private val cacheMissCounter        = new AtomicInteger(0)
    private val maxCalculationTime      = new AtomicInteger(0)
    private val totalCalculationTime    = new AtomicInteger(0)
    private val currentCalculationStart = ThreadLocal.withInitial[java.lang.Long](() => null)
    private val recursionDepth          = ThreadLocal.withInitial[Int](() => 0)

    override def invocation(): Unit = {
      invocationCounter.incrementAndGet()
    }

    override def calculationStart(): Unit = {
      cacheMissCounter.incrementAndGet()
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

    override protected def cacheHits  : Int = invocationCounter.get - cacheMisses
    override protected def cacheMisses: Int = cacheMissCounter.get
    override protected def maxTime    : Int = maxCalculationTime.get
    override protected def totalTime  : Int = totalCalculationTime.get
  }

  private object CollectorImpl {

    private val cacheStatsMap =
      new ConcurrentHashMap[String, CacheStatsCollector]()

    def getOrCreate(id: String, name: String): CacheStatsCollector = {
      cacheStatsMap.computeIfAbsent(id, new CollectorImpl(_, name))
    }

    def printReport(): Unit = {
      val data = cacheStatsMap.values().asScala.map(_.stats).toSeq.sortBy(_.name)
      CacheStats.printReport(data)
    }

    def clearAll(): Unit = cacheStatsMap.clear()
  }

}