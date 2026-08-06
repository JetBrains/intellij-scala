package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}

class ScalazTest extends FailableResolveTest("scalaz") {

  override protected def additionalLibraries: Seq[LibraryLoader] =
    IvyManagedLoader("org.scalaz" % "scalaz-core_2.11" % "7.1.0") :: Nil

  def testSCL7227(): Unit = doTest()
}
