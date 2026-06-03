package org.jetbrains.plugins.scala
package testingSupport.test.specs2

import com.intellij.execution.configurations._
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.testingSupport.test.CustomTestRunnerBasedStateProvider.TestFrameworkRunnerInfo
import org.jetbrains.plugins.scala.testingSupport.test._

class Specs2RunConfiguration(
  project: Project,
  configurationFactory: ConfigurationFactory,
  name: String
) extends AbstractTestRunConfiguration(
  project,
  configurationFactory,
  name
) {

  override val testFramework: Specs2TestFramework = Specs2TestFramework()

  override val configurationProducer: Specs2ConfigurationProducer = Specs2ConfigurationProducer()

  override protected val validityChecker: SuiteValidityChecker = Specs2RunConfiguration.validityChecker

  override def runStateProvider: RunStateProvider = new CustomTestRunnerOrSbtShellStateProvider(
    this,
    TestFrameworkRunnerInfo(classOf[org.jetbrains.plugins.scala.testingSupport.specs2.Specs2Runner]),
    new Spec2SbtTestRunningSupport(testConfigurationData)
  )
}

object Specs2RunConfiguration {

  private val validityChecker = new SuiteValidityCheckerBase {
    // SCL-12787: single parameters possible: class MySpec(implicit ee: ExecutionEnv) extends Specification
    override def hasSuitableConstructor(clazz: PsiClass): Boolean =
      isScalaObject(clazz) || hasPublicConstructor(clazz, maxParameters = 1)

    override def isValidClass(clazz: PsiClass): Boolean =
      clazz.is[ScClass, ScObject]
  }

  private def isScalaObject(clazz: PsiClass): Boolean =
    clazz.getQualifiedName.endsWith("$")
}