package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.DependencyManager._
import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.junit.experimental.categories.Category

/**
  * Created by kate on 4/7/16.
  */

//lots of self type in library, maybe this is cause of problem
@Category(Array(classOf[PerfCycleTests]))
class Postgres extends FailedResolveTest("postgresql") {

  override protected def additionalLibraries(): Seq[LibraryLoader] =
    IvyManagedLoader("com.wda.sdbc" % "postgresql_2.11" % "0.5") :: Nil

  def testSCL8556(): Unit = doTest()
}
