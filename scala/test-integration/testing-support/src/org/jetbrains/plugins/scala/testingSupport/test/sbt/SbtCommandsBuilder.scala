package org.jetbrains.plugins.scala.testingSupport.test.sbt

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.testingSupport.test.utils.StringOps

@ApiStatus.Internal
trait SbtCommandsBuilder {

  /**
   * Returns a sequence of arguments which will be passed to sbt `testOnly` command: {{{
   * testOnly <args1>
   * testOnly <args2>
   * ...
   * }}}
   *
   * Reminder from [[https://www.scala-sbt.org/1.x/docs/Testing.html#Options]]:<br>
   * Arguments to the test framework may be provided on the command line to the testOnly tasks
   * following a -- separator. For example:
   * {{{
   * testOnly org.example.MyTest -- -verbosity 1
   * }}}
   */
  def buildTestOnly(classToTests: Map[String, Set[String]]): Seq[String]
}

/**
 * Please, consider implementing [[SbtCommandsBuilder]] instead of inheriting this class
 */
@ApiStatus.Internal
abstract class SbtCommandsBuilderBase extends SbtCommandsBuilder {

  protected def classKey: Option[String] = None
  protected def testNameKey: Option[String] = None

  protected def escapeTestName(test: String): String = test.withQuotedSpaces.withoutBackticks
  protected  def escapeClassName(test: String): String = test.withQuotedSpaces.withoutBackticks

  /**
   * Examples for single return item:
   *  - `-classKey org.ClassName -testKey testName`
   *  - `org.ClassName -testKey testName`
   */
  override def buildTestOnly(classToTests: Map[String, Set[String]]): Seq[String] = {
    val result = classToTests
      .flatMap { case (clazz, testNames) =>
        if (testNames.isEmpty)
          Seq(argsForTestClass(clazz))
        else
          testNames.map(argsForTest(clazz, _))
      }
      .toSeq
    result
  }

  private def argsForTestClass(clazz: String): String =
    join(classKey.toList :+ escapeClassName(clazz))

  private def argsForTest(clazz: String, testName: String): String =
    join((classKey.toList :+ escapeClassName(clazz)) ++ testNameKey.toList :+ escapeTestName(testName))

  private def join(parts: Iterable[String]) =
    parts.mkString(" ")
}