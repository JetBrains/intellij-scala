package org.jetbrains.plugins.scala.caches.stats

case class CacheStats(name: String,
                      hits: Int,
                      misses: Int,
                      maxTime: Int,
                      totalTime: Int)

object CacheStats {

  def format(name: String, hits: String, misses: String, maxTime: String, totalTime: String): String =
    f"$name%-50s | $hits%10s | $misses%10s | $maxTime%10s | $totalTime%10s |"

  def format(stats: CacheStats): String = {
    import stats._

    import scala.language.implicitConversions

    implicit def toString(i: Int): String = i.toString

    CacheStats.format(name, hits, misses, maxTime, totalTime)
  }

  def title: String = format("", "Hits", "Misses", "Max Time", "Total time")

  def printReport(stats: Seq[CacheStats]): Unit = {
    val title = CacheStats.title
    println()
    println(title)
    println("-" * title.length)
    stats.map(format).foreach {
      println
    }
  }

}
