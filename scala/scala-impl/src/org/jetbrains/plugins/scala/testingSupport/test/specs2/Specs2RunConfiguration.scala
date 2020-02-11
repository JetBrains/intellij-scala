package org.jetbrains.plugins.scala
package testingSupport.test.specs2

import com.intellij.execution.configurations._
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.compiler.PluginJars
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestRunConfiguration.SettingMap
import org.jetbrains.plugins.scala.testingSupport.test._
import org.jetbrains.plugins.scala.testingSupport.test.testdata.{AllInPackageTestData, RegexpTestData}
import org.jetbrains.plugins.scala.util.ScalaUtil
import org.jetbrains.sbt.shell.SbtShellCommunication

import scala.concurrent.Future

/**
  * @author Ksenia.Sautina
  * @since 5/17/12
  */

class Specs2RunConfiguration(project: Project,
                             override val configurationFactory: ConfigurationFactory,
                             override val name: String)
  extends AbstractTestRunConfiguration(project, configurationFactory, name, TestConfigurationUtil.specs2ConfigurationProducer) {

  override def suitePaths: List[String] = Specs2Util.suitePaths

  override def runnerClassName = "org.jetbrains.plugins.scala.testingSupport.specs2.JavaSpecs2Runner"

  override def reporterClass = "org.jetbrains.plugins.scala.testingSupport.specs2.JavaSpecs2Notifier"

  override def errorMessage: String = "Specs2 is not specified"

  override def currentConfiguration: Specs2RunConfiguration = Specs2RunConfiguration.this

  protected[test] override def isInvalidSuite(clazz: PsiClass): Boolean = getSuiteClass.fold(_ => true, Specs2RunConfiguration.isInvalidSuite(clazz, _))

  override protected def sbtClassKey = " -- -specname "

  override protected def sbtTestNameKey = " -- -ex "

  //TODO temporarily disabled
  override def allowsSbtUiRun: Boolean = false

  override def modifySbtSettingsForUi(comm: SbtShellCommunication): Future[SettingMap] =
    modifySetting(SettingMap(), "fullClasspath", "test", "Test",
      "Attributed(new File(\"" + ScalaUtil.runnersPath().replace("\\", "\\\\") + "\"))(AttributeMap.empty)",
      comm, !_.contains(PluginJars.runnersJarName), shouldRevert = false)

  override def buildSbtParams(classToTests: Map[String, Set[String]]): Seq[String] = {
    testConfigurationData match {
      case regexpData: RegexpTestData =>
        val pattern = regexpData.zippedRegexps.head
        Seq(s"$sbtClassKey${pattern._1}$sbtTestNameKey${pattern._2}")
      case packageData: AllInPackageTestData =>
        Seq(s"$sbtClassKey${"\\A" + ScPackageImpl(packageData.getPackage(getTestPackagePath)).getQualifiedName + ".*"}")
      case _ =>
        super.buildSbtParams(classToTests)
    }
  }

  //TODO: should we have quotes here?
  override def escapeTestName(test: String): String = "\\A" + test + "\\Z"
}

object Specs2RunConfiguration extends SuiteValidityChecker {
  private def isScalaObject(clazz: PsiClass) = clazz.getQualifiedName.endsWith("$")

  override protected[test] def lackSuitableConstructor(clazz: PsiClass): Boolean =
    !isScalaObject(clazz) && AbstractTestRunConfiguration.lackSuitableConstructorWithParams(clazz, 1)

  override protected[test] def isInvalidClass(clazz: PsiClass): Boolean = !clazz.isInstanceOf[ScClass] && !clazz.isInstanceOf[ScObject]
}