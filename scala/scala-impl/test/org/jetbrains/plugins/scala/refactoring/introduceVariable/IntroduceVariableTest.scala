package org.jetbrains.plugins.scala
package refactoring
package introduceVariable

import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{HeavyJDKLoader, LibraryLoader, ScalaSDKLoader}
import org.junit.runner.RunWith
import org.junit.runners.AllTests

/**
  * Nikolay.Tropin
  * 25-Sep-17
  */
@RunWith(classOf[AllTests])
class IntroduceVariableTest extends AbstractIntroduceVariableTestBase(IntroduceVariableTest.DATA_PATH)
  with ScalaSdkOwner {

  override protected def librariesLoaders: Seq[LibraryLoader] = Seq(HeavyJDKLoader(), ScalaSDKLoader())
}

object IntroduceVariableTest {
  val DATA_PATH = "/introduceVariable/data"

  def suite = new IntroduceVariableTest
}
