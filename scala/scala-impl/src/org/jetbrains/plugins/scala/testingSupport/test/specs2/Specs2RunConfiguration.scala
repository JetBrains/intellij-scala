package org.jetbrains.plugins.scala
package testingSupport.test.specs2

import com.intellij.execution.configurations._
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.testIntegration.TestFramework
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration.{SettingMap, TestFrameworkRunnerInfo}
import org.jetbrains.plugins.scala.testingSupport.test._
import org.jetbrains.plugins.scala.testingSupport.test.sbt.{SbtCommandsBuilder, SbtCommandsBuilderBase, SbtTestRunningSupport, SbtTestRunningSupportBase}
import org.jetbrains.plugins.scala.testingSupport.test.specs2.Specs2RunConfiguration.Spec2SbtCommandsBuilder
import org.jetbrains.plugins.scala.testingSupport.test.testdata.{AllInPackageTestData, RegexpTestData, TestConfigurationData}
import org.jetbrains.plugins.scala.util.ScalaPluginJars
import org.jetbrains.sbt.shell.SbtShellCommunication

import scala.concurrent.Future

class Specs2RunConfiguration(
  project: Project,
  configurationFactory: ConfigurationFactory,
  name: String
) extends AbstractTestRunConfiguration(
  project,
  configurationFactory,
  name
) {

  override val suitePaths: List[String] = Specs2Util.suitePaths

  override val testFramework: TestFramework = TestFramework.EXTENSION_NAME.findExtension(classOf[Specs2TestFramework])

  override val configurationProducer: Specs2ConfigurationProducer = TestConfigurationUtil.specs2ConfigurationProducer

  override protected val validityChecker: SuiteValidityChecker = Specs2RunConfiguration.validityChecker

  override protected val runnerInfo: TestFrameworkRunnerInfo = TestFrameworkRunnerInfo(
    classOf[org.jetbrains.plugins.scala.testingSupport.specs2.Specs2Runner].getName
  )

  override val sbtSupport: SbtTestRunningSupport = new SbtTestRunningSupportBase {
    override def allowsSbtUiRun: Boolean = false //TODO: disabled due to SCL-11640, SCL-11638

    override def modifySbtSettingsForUi(comm: SbtShellCommunication): Future[SettingMap] = {
      val value = "Attributed(new File(\"" + ScalaPluginJars.runnersJar.getAbsolutePath.replace("\\", "\\\\") + "\"))(AttributeMap.empty)"
      modifySbtSetting(
        comm, getModule, SettingMap(), "fullClasspath", "test", "Test", value,
        !_.contains(ScalaPluginJars.runnersJarName),
        shouldRevert = false
      )
    }

    override def commandsBuilder: SbtCommandsBuilder =
      new Spec2SbtCommandsBuilder(testConfigurationData)
  }
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

  /**
   * @see [[https://etorreborre.github.io/specs2/guide/SPECS2-4.10.0/org.specs2.guide.Runners.html]]
   * @see [[https://etorreborre.github.io/specs2/guide/SPECS2-4.10.0/org.specs2.guide.Selection.html]]
   */
  @TestOnly
  final class Spec2SbtCommandsBuilder(testData: TestConfigurationData) extends SbtCommandsBuilderBase {
    private val _testNameKey: String = "-- -ex"

    override def classKey: Option[String] = None
    override def testNameKey: Option[String] = Some(_testNameKey)

    /**
     * NOTE:
     * For package and class names deliberately using "*" regex syntax, instead of usual ".*"
     * For class nad package names spec2 treats "." as part of package and automatically
     * transforms "*" to ".*" regex under the hood
     */
    override def buildTestOnly(classToTests: Map[String, Set[String]]): Seq[String] =
      testData match {
        case regexpData: RegexpTestData =>
          val (classPattern, testPattern) = regexpData.zippedRegexps.head
          val classPatternInSpec2Form = classPattern.replace(".*", "*")
          val command = s"$classPatternInSpec2Form ${_testNameKey} $testPattern"
          Seq(command)

        case packageData: AllInPackageTestData =>
          // why not just using packageData.testPackagePath ???
          val packageFqn = ScPackageImpl(packageData.getPackage(packageData.testPackagePath)).getQualifiedName
          val command = packageFqn + "*"
          Seq(command)

        case _ =>
          super.buildTestOnly(classToTests)
      }
  }

}