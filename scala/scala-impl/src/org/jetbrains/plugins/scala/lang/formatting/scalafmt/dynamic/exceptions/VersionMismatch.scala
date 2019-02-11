package org.jetbrains.plugins.scala.lang.formatting.scalafmt.dynamic.exceptions

case class VersionMismatch(obtainedVersion: String, expectedVersion: String)
    extends Exception(
      s"Scalafmt version mismatch. Version in .scalafmt.conf is '$obtainedVersion' and running version is '$expectedVersion'."
    )
