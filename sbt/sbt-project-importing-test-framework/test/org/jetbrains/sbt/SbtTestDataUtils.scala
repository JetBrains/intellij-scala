package org.jetbrains.sbt

import org.jetbrains.plugins.scala.util.TestUtils

// TODO: create a more universal utility and use in all modules,
//  something like TestDataPathUtil.Roots.Sbt/ScalaImpl or maybe something better...
object SbtTestDataUtils {

  private val SbtRootPath =
    TestUtils.findCommunityRootPath.resolve("sbt")

  def resolveRelativePath(relativePathFromSbtRoot: String): String =
    SbtRootPath
      .resolve(relativePathFromSbtRoot.stripPrefix("/"))
      .normalize()
      .toString
}
