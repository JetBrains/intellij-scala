package org.jetbrains.jps.incremental.scala.local.zinc

import org.jetbrains.jps.incremental.scala.local.Cache
import sbt.internal.inc.FileAnalysisStore
import xsbti.compile.AnalysisStore

import java.nio.file.Path

object AnalysisStoreFactory {
  private val analysisStoreCache: Cache[Path, AnalysisStore] = new Cache(600)

  def createAnalysisStore(analysisFile: Path): AnalysisStore =
    analysisStoreCache.getOrUpdate(analysisFile) { () =>
      // Zinc does not offer any other API, so using `java.io.File` here is unavoidable.
      // In practice, this should be fine, as Zinc (or scalac) usually runs in the same machine
      // where the code lives (i.e. inside WSL, if working with WSL).
      //noinspection SSBasedInspection
      val binary = FileAnalysisStore.binary(analysisFile.toFile)
      val cached = AnalysisStore.cached(binary)
      AnalysisStore.sync(cached)
    }
}
