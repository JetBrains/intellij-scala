package org.jetbrains.plugins.scala
package testingSupport.test.utest

import com.intellij.testIntegration.TestFramework
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.isInheritorDeep
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestFramework.TestFrameworkSetupInfo
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestFramework, TestFrameworkSetupSupportBase}

final class UTestTestFramework extends AbstractTestFramework with TestFrameworkSetupSupportBase {

  override def getName: String = "uTest"

  override def testFileTemplateName = "uTest Object"

  override def getMarkerClassFQName: String = "utest.TestSuite"

  override def getDefaultSuperClass: String = "utest.TestSuite"

  override def baseSuitePaths: Seq[String] = Seq("utest.framework.TestSuite", "utest.TestSuite")

  // overridden cause UTest now has 2 marker classes which are equal to suitePathes
  override protected def isTestClass(definition: ScTemplateDefinition): Boolean = {
    if (!definition.isInstanceOf[ScObject]) return false

    val elementScope = ElementScope(definition.getProject)
    val cachedClass = baseSuitePaths.iterator.flatMap(elementScope.getCachedClass).nextOption()
    cachedClass.exists(isInheritorDeep(definition, _))
  }

  override def frameworkSetupInfo(scalaVersion: Option[String]): TestFrameworkSetupInfo =
    TestFrameworkSetupInfo(Seq(""""com.lihaoyi" %% "utest" % "latest.integration" % Test"""), Seq())
}

object UTestTestFramework {

  @deprecated("use `apply` instead", "2020.3")
  def instance: UTestTestFramework = apply()

  def apply(): UTestTestFramework =
    TestFramework.EXTENSION_NAME.findExtension(classOf[UTestTestFramework])
}