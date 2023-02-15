package org.jetbrains.jps.incremental.scala.local.zinc

import sbt.internal.inc.PlainVirtualFileConverter
import xsbti.FileConverter

object Utils {
  val virtualFileConverter: FileConverter = new PlainVirtualFileConverter()
}
