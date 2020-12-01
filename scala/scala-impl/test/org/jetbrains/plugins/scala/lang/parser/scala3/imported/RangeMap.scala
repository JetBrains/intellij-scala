package org.jetbrains.plugins.scala.lang.parser.scala3.imported

import com.intellij.openapi.util.TextRange

import java.io.FileNotFoundException
import java.nio.file.Path
import scala.collection.immutable.SortedMap
import scala.io.Source
import scala.util.Using


case class RangeMap[+T] private(private val sortedRanges: SortedMap[Int, (TextRange, T)]) {
  def intersections(range: TextRange): Iterator[(TextRange, T)] =
    sortedRanges
      .iteratorFrom(range.getStartOffset)
      .takeWhile { case (p, _) => p <= range.getEndOffset }
      .map(_._2)

  /**
   * Get all ranges that are interlaced with `range`
   *
   * Two ranges are interlaced if they intersect but neither is a sub-range of the other.
   */
  def interlaced(range: TextRange): Iterator[(TextRange, T)] =
    intersections(range).filter(r => RangeMap.interlaced(r._1, range))
}

object RangeMap {
  private val lineRegex = raw"\[(\d+),(\d+)\]: (.+)$$".r

  val empty: RangeMap[Nothing] = RangeMap(SortedMap.empty)

  def fromFile(path: Path): RangeMap[String] =
    RangeMap(Using.resource(Source.fromFile(path.toFile)) {
      _.getLines()
        .flatMap {
          case lineRegex(start, end, name) => Some((new TextRange(start.toInt, end.toInt), name))
          case _ => None
        }
        .flatMap { case t@(r, _) => Seq(r.getStartOffset -> t, r.getEndOffset -> t) }
        .to(SortedMap)
    })

  def fromFileOrEmpty(path: Path): RangeMap[String] =
    try fromFile(path)
    catch {
      case _: FileNotFoundException => empty
    }

  private def interlaced(a: TextRange, b: TextRange): Boolean =
    a.intersectsStrict(b) && !a.contains(b) && !b.contains(a)
}
