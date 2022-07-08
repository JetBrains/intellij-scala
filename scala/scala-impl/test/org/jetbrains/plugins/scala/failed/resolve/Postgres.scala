package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}

//lots of self type in library, maybe this is cause of problem
class Postgres extends FailableResolveTest("postgresql") {

  override protected def additionalLibraries: Seq[LibraryLoader] =
    IvyManagedLoader("com.wda.sdbc" % "postgresql_2.11" % "0.5") :: Nil

  def testSCL8556(): Unit = doTest()
}
