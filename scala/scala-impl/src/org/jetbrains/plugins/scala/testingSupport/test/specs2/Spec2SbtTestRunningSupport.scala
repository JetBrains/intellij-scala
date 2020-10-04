package org.jetbrains.plugins.scala.testingSupport.test.specs2

import com.intellij.openapi.module.Module
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration.SettingMap
import org.jetbrains.plugins.scala.testingSupport.test.sbt.{SbtCommandsBuilder, SbtCommandsBuilderBase, SbtTestRunningSupportBase}
import org.jetbrains.plugins.scala.testingSupport.test.specs2.Spec2SbtTestRunningSupport.Spec2SbtCommandsBuilder
import org.jetbrains.plugins.scala.testingSupport.test.testdata.{AllInPackageTestData, RegexpTestData, TestConfigurationData}
import org.jetbrains.plugins.scala.util.ScalaPluginJars
import org.jetbrains.sbt.shell.SbtShellCommunication

import scala.concurrent.Future

private class Spec2SbtTestRunningSupport(testData: TestConfigurationData) extends SbtTestRunningSupportBase {
  override def allowsSbtUiRun: Boolean = false //TODO: disabled due to SCL-11640, SCL-11638

  override def modifySbtSettingsForUi(module: Module, comm: SbtShellCommunication): Future[SettingMap] = {
    val value = "Attributed(new File(\"" + ScalaPluginJars.runnersJar.getAbsolutePath.replace("\\", "\\\\") + "\"))(AttributeMap.empty)"
    modifySbtSetting(
      comm, module, SettingMap(), "fullClasspath", "test", "Test", value,
      !_.contains(ScalaPluginJars.runnersJarName),
      shouldRevert = false
    )
  }

  override def commandsBuilder: SbtCommandsBuilder =
    new Spec2SbtCommandsBuilder(testData)
}

object Spec2SbtTestRunningSupport {

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