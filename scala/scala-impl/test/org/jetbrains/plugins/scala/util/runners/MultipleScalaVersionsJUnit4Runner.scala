package org.jetbrains.plugins.scala.util.runners

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.junit.runner.Runner
import org.junit.runners.{BlockJUnit4ClassRunner, Suite}
import org.junit.runners.model.{FrameworkMethod, InvalidTestClassError}

import scala.jdk.CollectionConverters._

/**
 * Custom JUnit 4 runner for running a test with multiple Scala and JDK versions.
 * It is a JUnit 4 based replacement for [[MultipleScalaVersionsRunner]].
 *
 * This runner is an improvement over [[MultipleScalaVersionsRunner]] because it allows each test case to have a unique
 * name reported in sbt and TeamCity. This helps with the reported execution time of each test and will help us with the
 * automatic bucketing of tests in the future.
 *
 * It extends the [[Suite]] JUnit 4 runner. Behind the scenes, a test suite is created for each pair of
 * Scala and JDK versions.
 *
 * Scala versions can be specified using the [[RunWithScalaVersions]] annotation.
 * JDK versions can be specified using the [[RunWithJdkVersions]] annotation.
 *
 * @note Must be applied to a test which mixes in the trait [[ScalaSdkOwner]].
 * @note Because this is a JUnit 4 test runner, only the methods annotated with [[org.junit.Test]] are executed.
 * @note Use `@RunWith(classOf[MultipleScalaVersionsRuner])` to run a test with this runner.
 * @param cls The test class instance provided reflectively by the JUnit 4 runtime.
 */
class MultipleScalaVersionsJUnit4Runner(cls: Class[?])
  extends Suite(cls, MultipleScalaVersionsJUnit4Runner.createRunners(cls).asJava)

private object MultipleScalaVersionsJUnit4Runner {

  /**
   * Inspired by [[org.junit.runners.Parameterized]].
   */
  private def createRunners(cls: Class[?]): Seq[Runner] = {
    val assignable = classOf[ScalaSdkOwner].isAssignableFrom(cls)
    if (!assignable) {
      val notScalaSdkOwner = new Exception(s"Test class ${cls.getName} must mix-in the trait ${classOf[ScalaSdkOwner].getName}")
      throw new InvalidTestClassError(cls, java.util.List.of(notScalaSdkOwner))
    }
    val scalaSdkOwnerCls = cls.asInstanceOf[Class[? <: ScalaSdkOwner]]
    val scalaVersions = MultipleScalaVersionsRunner.scalaVersionsToRun(scalaSdkOwnerCls)

    val registryValue = MultipleScalaVersionsRunner.filterJdkVersionRegistry
    val jdkFilter = (version: TestJdkVersion) => registryValue.forall(_ == version)
    val jdkVersions = MultipleScalaVersionsRunner.jdkVersionsToRun(scalaSdkOwnerCls).filter(jdkFilter)

    for {
      sv <- scalaVersions
      jv <- jdkVersions
    } yield new InjectedScalaAndJdkVersionRunner(scalaSdkOwnerCls, sv.toProductionVersion, jv.toProductionVersion)
  }

  /**
   * A JUnit 4 runner which runs all tests annotated with the [[org.junit.Test]] annotation. The only custom logic
   * is the injection of the Scala version and the JDK version which the specified tests will be running against.
   *
   * @param cls          The test class.
   * @param scalaVersion The Scala version to be injected.
   * @param jdkVersion   The JDK version to be injected.
   */
  private final class InjectedScalaAndJdkVersionRunner(
    cls: Class[? <: ScalaSdkOwner],
    scalaVersion: ScalaVersion,
    jdkVersion: LanguageLevel
  ) extends BlockJUnit4ClassRunner(cls) {
    override def createTest(): ScalaSdkOwner = {
      val instance = getTestClass.getOnlyConstructor.newInstance().asInstanceOf[ScalaSdkOwner]
      instance.injectedScalaVersion = scalaVersion
      instance.injectedJdkVersion = jdkVersion
      instance
    }

    /**
     * Similar to [[Suite]] and [[org.junit.runners.Parameterized]], a suite is created which contains the names
     * of the Scala version and the JDK version which apply to the tests being run in that suite.
     */
    override def getName: String = s"[${scalaVersion.minor}, ${jdkVersion.name()}]"

    /**
     * Similar to [[org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters]], the Scala version and
     * the JDK version are included in the test name, as if they were test parameters. This ensures that each test case
     * is reported in sbt and TeamCity as a unique test and not as the same test executed multiple times, which leads
     * to some confusion about the test runtime.
     */
    override def testName(method: FrameworkMethod): String = method.getName + getName
  }
}
