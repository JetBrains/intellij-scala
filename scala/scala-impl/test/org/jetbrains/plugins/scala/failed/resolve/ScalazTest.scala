package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.DependencyManager._
import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.junit.experimental.categories.Category

/**
  * Created by kate on 3/29/16.
  */

@Category(Array(classOf[PerfCycleTests]))
class ScalazTest extends FailedResolveTest("scalaz") {

  override protected def additionalLibraries(): Seq[LibraryLoader] =
    IvyManagedLoader("org.scalaz" % "scalaz-core_2.11" % "7.1.0") :: Nil

  def testSCL7213(): Unit = doTest()

  def testSCL7227(): Unit = doTest()
}
