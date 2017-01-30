package org.jetbrains.plugins.scala.failed.resolve

import com.intellij.openapi.module.Module
import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.libraryLoaders.{SlickLoader, ThirdPartyLibraryLoader}
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class SlickTest extends FailedResolveTest("slick") {

  override protected def additionalLibraries(module: Module): Array[ThirdPartyLibraryLoader] =
    Array(SlickLoader()(module))

  def testSCL8829() = doTest()
}
