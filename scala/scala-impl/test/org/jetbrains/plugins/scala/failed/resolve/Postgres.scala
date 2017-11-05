package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.libraryLoaders.{PostgresLoader, ThirdPartyLibraryLoader}
import org.junit.experimental.categories.Category

/**
  * Created by kate on 4/7/16.
  */

//lots of self type in library, maybe this is cause of problem
@Category(Array(classOf[PerfCycleTests]))
class Postgres extends FailedResolveTest("postgresql") {

  override protected def additionalLibraries(): Seq[ThirdPartyLibraryLoader] =
    Seq(PostgresLoader()(module))

  def testSCL8556(): Unit = doTest()
}
