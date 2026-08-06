package org.jetbrains.sbt

import org.jetbrains.plugins.scala.project.Version

trait MinorVersionGenerator[T <: Ordered[T]] {
  def minor: String
  def generateNewVersion(version: String): Option[T]

  /**
   * @example `1.11.2` -> Seq(`1.11.2`, `1.11.1`, `1.11.0`)<br>
   *          `1.11.2-RC3` -> Seq(`1.11.2-RC3`, `1.11.1`, `1.11.0`)
   */
  final def generateAllMinorVersions: Seq[T] = {
    val fullVersion = this.minor

    val versionParts = Version.findAllNumbersInVersion(fullVersion)
    if (versionParts.length < 2)
      return Nil

    // in "2.13.1" it's "2.13"
    val majorPart = versionParts.take(2).mkString(".")
    // in "2.13.1" it's "13"
    val minorNumber = versionParts(2)

    // (e.g., if it was 2.1.0-RC3, we don't want to lose the RC3 suffix)
    val allOlderMinorNumbers = 0 until minorNumber.toInt
    val allOlderVersions = allOlderMinorNumbers.flatMap { minorNumberNew =>
      generateNewVersion(s"$majorPart.${minorNumberNew.toString}")
    }

    // Keep the original full version unmodified
    val originalVersion = generateNewVersion(fullVersion)
    allOlderVersions ++ originalVersion
  }
}

object MinorVersionGenerator {

  def generateAllMinorVersions[V <: Ordered[V], T <: MinorVersionGenerator[V]](versions: Seq[T], mapToString: V => String): List[String] = {
    val allMinorVersions = versions.flatMap(_.generateAllMinorVersions)
    val uniquePresented = allMinorVersions.distinct.map(mapToString)
    uniquePresented.toList
  }
}
