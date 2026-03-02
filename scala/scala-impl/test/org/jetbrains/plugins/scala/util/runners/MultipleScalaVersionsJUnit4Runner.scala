package org.jetbrains.plugins.scala.util.runners

import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.{InjectableJdk, ScalaSdkOwner}
import org.jetbrains.plugins.scala.util.Annotations
import org.jetbrains.plugins.scala.util.teamcity.TeamcityUtils
import org.jetbrains.plugins.scala.util.teamcity.TeamcityUtils.Status.Warning
import org.junit.Test
import org.junit.runner.Runner
import org.junit.runners.model.{FrameworkMethod, InvalidTestClassError}
import org.junit.runners.{BlockJUnit4ClassRunner, Suite}

import java.lang.reflect.{Method, Modifier}
import scala.jdk.CollectionConverters._

/**
 * Custom JUnit 4 runner for running a test with multiple Scala and JDK versions.
 *
 * It extends the [[Suite]] JUnit 4 runner. Behind the scenes, a test suite is created for each pair of
 * Scala and JDK versions.
 *
 * Scala versions can be specified using the [[RunWithScalaVersions]] annotation.
 * JDK versions can be specified using the [[RunWithJdkVersions]] annotation.
 *
 * @note Must be applied to a test which mixes in the trait [[ScalaSdkOwner]].
 * @note Because this is a JUnit 4 test runner, only the methods annotated with [[org.junit.Test]] are executed.
 * @note Use `@RunWith(classOf[MultipleScalaVersionsJUnit4Runner])` to run a test with this runner.
 * @note The result of [[ScalaSdkOwner.skip]] is not taken into account by this runner. This matches the philosophy of
 *       JUnit 4 and matches the [[org.junit.runners.Parameterized]] runner. If a combination of Scala and JDK version
 *       is specified, a corresponding suite will be created and the tests will be run.
 * @param cls The test class instance provided reflectively by the JUnit 4 runtime.
 */
class MultipleScalaVersionsJUnit4Runner(cls: Class[?])
  extends Suite(cls, MultipleScalaVersionsJUnit4Runner.createRunners(cls).asJava)

object MultipleScalaVersionsJUnit4Runner {

  private val DefaultScalaVersionsToRun: Seq[TestScalaVersion] =
    Seq(
      TestScalaVersion.Scala_2_11,
      TestScalaVersion.Scala_2_12,
      TestScalaVersion.Scala_2_13,
    )

  private val DefaultJdkVersionToRun: TestJdkVersion =
    TestJdkVersion.from(InjectableJdk.DefaultJdk)

  private lazy val filterJdkVersionRegistry: Option[TestJdkVersion] = {
    val result = Option(System.getProperty("filter.test.jdk.version")).map(TestJdkVersion.valueOf)
    result.foreach(v => TeamcityUtils.logUnderTeamcity(s"MultipleScalaVersionsJUnit4Runner: running jdk filter: $v", status = Warning))
    result
  }

  private def scalaVersionsToRun(klass: Class[_ <: ScalaSdkOwner]): Seq[TestScalaVersion] = {
    val annotation = Annotations.findAnnotation(klass, classOf[RunWithScalaVersions])
    annotation
      .map(_.value.toSeq)
      .getOrElse(DefaultScalaVersionsToRun)
  }

  private def jdkVersionsToRun(klass: Class[_ <: ScalaSdkOwner]): Seq[TestJdkVersion] = {
    val annotation = Annotations.findAnnotation(klass, classOf[RunWithJdkVersions])
    annotation
      .map(_.value.toSeq)
      .getOrElse(Seq(DefaultJdkVersionToRun))
  }

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
    val scalaVersions = scalaVersionsToRun(scalaSdkOwnerCls)

    val jdkFilter = (version: TestJdkVersion) => filterJdkVersionRegistry.forall(_ == version)
    val jdkVersions = jdkVersionsToRun(scalaSdkOwnerCls).filter(jdkFilter)

    for {
      sv <- scalaVersions.map(_.toProductionVersion).distinct
      jv <- jdkVersions.map(_.toProductionVersion).distinct
    } yield new InjectedScalaAndJdkVersionRunner(scalaSdkOwnerCls, sv, jv)
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

    validateNoUnmigratedJUnit3TestDefinitions(cls)

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

    private def validateNoUnmigratedJUnit3TestDefinitions(testClass: Class[? <: ScalaSdkOwner]): Unit = {
      val allPublicMethods = testClass.getMethods
      val unmigratedMethods = allPublicMethods.filter(isUnmigratedJUnit3TestDefinition)
      if (unmigratedMethods.nonEmpty) {
        val message = unmigratedMethods.map(m => s"     - ${m.getName}")
          .mkString(
            start = s"The test class ${testClass.getName} contains unmigrated JUnit 3 style test methods:\n",
            sep = "\n",
            end = "\n     Please annotate these methods with @org.junit.Test to make them executable with MultipleScalaVersionsJUnit4Runner."
          )
        val exception = new Exception(message)
        throw new InvalidTestClassError(testClass, java.util.List.of(exception))
      }
    }

    private def isUnmigratedJUnit3TestDefinition(method: Method): Boolean = {
      val isPublic = Modifier.isPublic(method.getModifiers)
      val startsWithTest = method.getName.startsWith("test")
      val hasZeroParameters = method.getParameters.isEmpty
      val returnsVoid = method.getReturnType == java.lang.Void.TYPE
      val hasTestAnnotation = method.getAnnotation(classOf[Test]) != null
      isPublic && startsWithTest && hasZeroParameters && returnsVoid && !hasTestAnnotation
    }
  }
}
