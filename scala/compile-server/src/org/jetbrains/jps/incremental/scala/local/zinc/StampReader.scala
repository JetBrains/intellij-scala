package org.jetbrains.jps.incremental.scala.local.zinc

import sbt.internal.inc.{PlainVirtualFileConverter, Stamps}
import xsbti.compile.analysis.ReadStamps

private[local] object StampReader {
  val Instance: ReadStamps = Stamps.timeWrapBinaryStamps(PlainVirtualFileConverter.converter)
}
