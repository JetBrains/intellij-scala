package org.jetbrains.plugins.scala
package testingSupport.test.specs2

import com.intellij.testIntegration.TestFramework
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestFramework.TestFrameworkSetupInfo
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestFramework, TestFrameworkSetupSupportBase}

final class Specs2TestFramework extends AbstractTestFramework with TestFrameworkSetupSupportBase {

  override def getName: String = "Specs2"

  override def testFileTemplateName = "Specs2 Class"

  override def getMarkerClassFQName: String = "org.specs2.mutable.Specification"

  override def getDefaultSuperClass: String = "org.specs2.mutable.Specification"

  override def baseSuitePaths: Seq[String] = Seq(
    "org.specs2.specification.SpecificationStructure",
    "org.specs2.specification.core.SpecificationStructure"
  )

  override def frameworkSetupInfo(scalaVersion: Option[String]): TestFrameworkSetupInfo =
    TestFrameworkSetupInfo(
      Seq(""""org.specs2" %% "specs2-core" % "latest.integration" % Test"""),
      Seq(""""-Yrangepos"""")
    )
}

object Specs2TestFramework {

  @deprecated("use `apply` instead", "2020.3")
  def instance: Specs2TestFramework = apply()

  def apply(): Specs2TestFramework =
    TestFramework.EXTENSION_NAME.findExtension(classOf[Specs2TestFramework])
}
