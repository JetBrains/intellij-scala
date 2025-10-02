package org.jetbrains.plugins.scala.mlCompletion

import org.junit.Test

class ScalaMlRankingProviderTest:
  @Test
  def modelMetadataConsistency(): Unit = ScalaMlRankingProvider().assertModelMetadataConsistent()
