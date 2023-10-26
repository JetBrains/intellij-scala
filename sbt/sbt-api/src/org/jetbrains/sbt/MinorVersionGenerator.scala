package org.jetbrains.sbt

import org.jetbrains.plugins.scala.project.Version

import scala.collection.immutable.SortedSet

trait MinorVersionGenerator[T <: Ordered[T]] {
  def minor: String
  def generateNewVersion(version: String): Option[T]

  def generateAllMinorVersions(): Seq[T] = {
    val versions = Version.findAllNumbersInVersion(minor)
    versions.lift(2).map { minorSuffix =>
      val major = versions.take(2).mkString(".")
      (0 to minorSuffix.toInt)
        .flatMap(suffix => generateNewVersion(s"$major.${suffix.toString}"))
    }.getOrElse(Seq.empty)
  }
}

object MinorVersionGenerator {

  def generateAllMinorVersions[V <: Ordered[V], T <: MinorVersionGenerator[V]](versions: Seq[T], mapToString: V => String): List[String] =
    SortedSet.from(versions.flatMap(_.generateAllMinorVersions()))
      .map(mapToString)
      .toList

}
