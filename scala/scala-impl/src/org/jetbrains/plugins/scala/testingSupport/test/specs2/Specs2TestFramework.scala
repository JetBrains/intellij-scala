package org.jetbrains.plugins.scala
package testingSupport.test.specs2

import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestFramework, TestFrameworkSetupSupport, TestFrameworkSetupSupportBase}
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestFramework.TestFrameworkSetupInfo

class Specs2TestFramework extends AbstractTestFramework with TestFrameworkSetupSupportBase {

  override def getName: String = "Specs2"

  override def testFileTemplateName = "Specs2 Class"

  override def getMarkerClassFQName: String = "org.specs2.mutable.Specification"

  override def getDefaultSuperClass: String = "org.specs2.mutable.Specification"

  override def baseSuitePaths: Seq[String] = Specs2Util.suitePaths

  override def frameworkSetupInfo(scalaVersion: Option[String]): TestFrameworkSetupInfo =
    TestFrameworkSetupInfo(
      Seq(""""org.specs2" %% "specs2-core" % "latest.integration" % "test""""),
      Seq(""""-Yrangepos"""")
    )
}
