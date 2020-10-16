package org.jetbrains.plugins.scala.testingSupport.test.munit

import com.intellij.testIntegration.TestFramework
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestFramework

final class MUnitTestFramework extends AbstractTestFramework {

  override def baseSuitePaths: Seq[String] = Seq("munit.Suite")

  override def getMarkerClassFQName: String = "munit.Suite"

  override def getName: String = "MUnit"

  override def getDefaultSuperClass: String = MUnitUtils.FunSuiteFqn
}

object MUnitTestFramework {

  def apply(): MUnitTestFramework =
    TestFramework.EXTENSION_NAME.findExtension(classOf[MUnitTestFramework])
}
