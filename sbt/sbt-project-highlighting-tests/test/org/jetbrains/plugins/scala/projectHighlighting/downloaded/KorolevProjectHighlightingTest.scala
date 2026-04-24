package org.jetbrains.plugins.scala.projectHighlighting.downloaded

import org.jetbrains.plugins.scala.projectHighlighting.base.GithubRepositoryWithRevision

class KorolevProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {

  //version 0.9.0
  override protected def githubRepositoryWithRevision: GithubRepositoryWithRevision =
    GithubRepositoryWithRevision("fomkin", "korolev", "7d4d41113b574061aedb5a791747b0fa5d122fdf")
}
