package org.jetbrains.plugins.scala.tasty

import java.util.UUID

// See dotty.tools.tasty.TastyHeader

private case class TastyHeader(uuid: UUID,
                               majorVersion: Int,
                               minorVersion: Int,
                               experimentalVersion: Int,
                               toolingVersion: String)
