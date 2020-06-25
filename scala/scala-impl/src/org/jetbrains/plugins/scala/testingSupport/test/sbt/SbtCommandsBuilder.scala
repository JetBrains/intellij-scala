package org.jetbrains.plugins.scala.testingSupport.test.sbt

private[test]
trait SbtCommandsBuilder {

  def buildTestOnly(classToTests: Map[String, Set[String]]): Seq[String]
}

// TODO: test all inheritors
private[test]
abstract class SbtCommandsBuilderBase extends SbtCommandsBuilder {

  protected def classKey: Option[String] = None
  protected def testNameKey: Option[String] = None
  protected def escapeClassAndTest(input: String): String = input
  protected def escapeTestName(test: String): String = quoteSpaces(test)

  def buildTestOnly(classToTests: Map[String, Set[String]]): Seq[String] =
    classToTests.flatMap { case (aClass, tests) =>
      if (tests.isEmpty)
        Seq(withClassKey(aClass))
      else
        tests.map { test =>
          val classAndTest = (Seq(aClass) ++ testNameKey :+ escapeTestName(test)).map(_.trim).mkString(" ")
          withClassKey(escapeClassAndTest(classAndTest))
        }
    }.toSeq

  protected final def quoteSpaces(text: String): String =
    if (text.contains(" ")) s""""$text"""" else text

  private def withClassKey(value: String): String =
    classKey.fold(" ")(key => key.trim + " " + sanitize(value).trim)

  // TODO: extract to utils, rename
  private def sanitize(qualifiedName: String): String = qualifiedName.replace("`", "")
}