package org.jetbrains.plugins.scala.testingSupport
package test.munit

import com.intellij.execution.configurations.{JavaParameters, ParametersList}
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.execution.testframework.TestSearchScope
import com.intellij.execution.{ExecutionException, ExecutionResult, Executor, JavaTestFrameworkRunnableState}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.rt.junit.JUnitStarter
import com.intellij.util.PathUtil
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.testingSupport.test.exceptions
import org.jetbrains.plugins.scala.testingSupport.test.munit.MUnitCommandLineState.Log
import org.jetbrains.plugins.scala.testingSupport.test.testdata.{AllInPackageTestData, TestConfigurationData}
import org.jetbrains.plugins.scala.testingSupport.test.utils.RawProcessOutputDebugLogger

import java.io.File
import scala.jdk.CollectionConverters.SeqHasAsJava

/**
 * @todo We reuse [[com.intellij.rt.junit.JUnitStarter]] so we depend on bundled JUnit plugin at runtime.<br>
 *       We should extract munit support to a separate module (SCL-13080) or even plugin.<br>
 *       Munit support should automatically be disabled when JUnit plugin is disabled manually<br>
 *       (though it's not a often case)
 */
private class MUnitCommandLineState(
  conf: MUnitConfiguration,
  env: ExecutionEnvironment,
  failedTests: Option[Seq[(String, String)]]
) extends JavaTestFrameworkRunnableState[MUnitConfiguration](env) {

  override def getFrameworkName: String = "MUnit"

  override def getFrameworkId: String = "munit"

  override def getConfiguration: MUnitConfiguration = conf

  override def getScope: TestSearchScope = testData.searchTest.toPlatformTestSearchScope

  private def testData: TestConfigurationData = conf.testConfigurationData

  override def configureRTClasspath(javaParameters: JavaParameters, module: Module): Unit = {
    val classPath = javaParameters.getClassPath
    try {
      classPath.addFirst(PathUtil.getJarPathForClass(classOf[JUnitStarter]))
    } catch {
      case noCassDef: NoClassDefFoundError if noCassDef.getMessage.toLowerCase.contains("junit") =>
        Log.warn(noCassDef)
        throw new ExecutionException(TestingSupportBundle.message("ensure.junit.plugin.is.enabled"))
    }
  }

  // no fork supported for now
  override def getForkMode: String = JUnitConfiguration.FORK_NONE

  override def passForkMode(forkMode: String, tempFile: File, parameters: JavaParameters): Unit = ()

  override def passTempFile(parametersList: ParametersList, tempFilePath: String): Unit =
    parametersList.add("@" + tempFilePath)

  override def deleteTempFiles(): Unit =
    super.deleteTempFiles()

  @throws[ExecutionException]
  override protected def createJavaParameters: JavaParameters = {
    val javaParameters = super.createJavaParameters

    javaParameters.setMainClass(JUnitConfiguration.JUNIT_START_CLASS)
    javaParameters.getProgramParametersList.add(JUnitStarter.IDE_VERSION + JUnitStarter.VERSION)

    val preferredRunner = com.intellij.rt.junit.JUnitStarter.JUNIT4_PARAMETER // munit works with junit 4
    javaParameters.getProgramParametersList.add(preferredRunner)

    val suitesToTestsMap = failedTests match {
      case Some(failed: Seq[(String, String)]) =>
        val grouped = failed.groupMap(_._1)(_._2).view.mapValues(_.toSet)
        grouped.filter(_._2.nonEmpty)
      case None                                =>
        testData.getTestMap
    }

    import exceptions._
    if (suitesToTestsMap.isEmpty)
      throw configurationException(TestingSupportBundle.message("munit.command.line.state.no.tests.found"))

    val isSingleClass = suitesToTestsMap.size == 1 && suitesToTestsMap.head._2.isEmpty
    if (isSingleClass) {
      val clazzName = suitesToTestsMap.head._1
      javaParameters.getProgramParametersList.add(clazzName)
    }
    else {
      /** see [[com.intellij.junit4.JUnit4TestRunnerUtil.buildRequest]] */
      val testNames: Seq[String] =
        suitesToTestsMap.flatMap { case (clazz, tests) =>
          if (tests.isEmpty)
            Seq(clazz)
          else
            tests.map(test => s"$clazz,$test")
        }.toSeq

      val packageName = testData.asOptionOf[AllInPackageTestData].fold("")(_.testPackagePath)

      val category = ""
      val filters: String = ""

      createTempFiles(javaParameters)
      JUnitStarter.printClassesList(testNames.asJava, packageName, category, filters, myTempFile)
    }

    javaParameters
  }

  override def execute(executor: Executor, runner: ProgramRunner[_]): ExecutionResult = {
    val result = super.execute(executor, runner)
    RawProcessOutputDebugLogger.maybeAddListenerTo(result.getProcessHandler)
    result
  }
}

object MUnitCommandLineState {
  private val Log = Logger.getInstance(classOf[MUnitCommandLineState])
}
