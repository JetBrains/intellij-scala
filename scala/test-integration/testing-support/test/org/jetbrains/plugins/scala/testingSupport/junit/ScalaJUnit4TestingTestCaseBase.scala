package org.jetbrains.plugins.scala.testingSupport.junit

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}

abstract class ScalaJUnit4TestingTestCaseBase extends ScalaJUnitTestingTestCaseBase{
  override protected def additionalLibraries: Seq[LibraryLoader] = Seq(
    IvyManagedLoader(("junit" % "junit" % "4.13.2").transitive())
  )
}
