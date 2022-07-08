package org.jetbrains.plugins.scala
package refactoring
package introduceVariable

import junit.framework.{Test, TestCase}
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.base.libraryLoaders.{HeavyJDKLoader, LibraryLoader, ScalaSDKLoader}

class IntroduceVariableTest extends TestCase

object IntroduceVariableTest {
  val DATA_PATH = "/introduceVariable/data"

  def suite(): Test = new AbstractIntroduceVariableTestBase(IntroduceVariableTest.DATA_PATH) with ScalaSdkOwner {
    override protected def librariesLoaders: Seq[LibraryLoader] = Seq(HeavyJDKLoader(), ScalaSDKLoader())
  }
}
