package org.jetbrains.jps.incremental.scala.local.zinc

import sbt.internal.inc.{FarmHash, PlainVirtualFileConverter, Stamper, Stamps}
import xsbti.VirtualFileRef
import xsbti.compile.analysis.{ReadStamps, Stamp}

private[local] object StampReader {

  private def avoidSigs(ref: VirtualFileRef): Stamp = {
    if (ref.id.endsWith(".sig")) {
      val path = PlainVirtualFileConverter.converter.toPath(ref)
      if (path.getClass.getName == "jdk.nio.zipfs.ZipPath")
        return FarmHash.fromLong(path.##.toLong)
    }
    fallback(ref)
  }

  private def fallback(ref: VirtualFileRef): Stamp =
    Stamper.forHashInRootPaths(PlainVirtualFileConverter.converter).apply(ref)

  private val uncached: ReadStamps = Stamps.uncachedStamps(
    avoidSigs,
    Stamper.forContentHash,
    avoidSigs
  )

  val Instance: ReadStamps = Stamps.timeWrapBinaryStamps(uncached, PlainVirtualFileConverter.converter)
}
