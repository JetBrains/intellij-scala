package org.jetbrains.plugins.scala.mlCompletion

import junit.framework.TestCase

class ScalaMlRankingProviderTest extends TestCase {
  def testModelMetadataConsistency(): Unit = new ScalaMlRankingProvider().assertModelMetadataConsistent()
}
