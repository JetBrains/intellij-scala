package org.jetbrains.plugins.scala.annotator.createFromUsage

import com.intellij.testFramework.EditorTestUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.codeInspection.ScalaAnnotatorQuickFixTestBase
import org.jetbrains.plugins.scala.lang.formatter.scalafmt.ScalaFmtForTestsSetupOps
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith
import org.scalafmt.dynamic.ScalafmtVersion

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13
))
abstract class CreateMethodQuickFixTestBase extends ScalaAnnotatorQuickFixTestBase {
  protected val Start = EditorTestUtil.SELECTION_START_TAG
  protected val End = EditorTestUtil.SELECTION_END_TAG
  protected val Caret = EditorTestUtil.CARET_TAG

  protected val methodName = "foo"
  private val hint = s"Create method '$methodName'"

  override protected val description = s"Cannot resolve symbol $methodName"

  protected def doTest(@Language("Scala") before: String, @Language("Scala") after: String): Unit =
    testQuickFix(before, after, hint)

  protected def doCompoundTest(methodUsageText: String, methodDefinitionText: String): Unit = {
    doTestInObject(methodUsageText, methodDefinitionText)
    doTestInClass(methodUsageText, methodDefinitionText)
    doTestInSameClass(methodUsageText, methodDefinitionText)
    doTestInSameClassWithThis(methodUsageText, methodDefinitionText)
  }

  protected def doTestInObject(methodUsageText: String, methodDefinitionText: String): Unit = {
    val before =
      s"""object Bar {
         |  def someOtherMethod = 42
         |}
         |object Usage {
         |  Bar.$Caret$methodUsageText
         |}""".stripMargin
    val after =
      s"""object Bar {
         |  def someOtherMethod = 42
         |
         |  $methodDefinitionText
         |}
         |object Usage {
         |  Bar.$methodUsageText
         |}""".stripMargin
    doTest(before, after)
  }

  protected def doTestInClass(methodUsageText: String, methodDefinitionText: String): Unit = {
    val before =
      s"""class Bar {
         |  def someOtherMethod = 42
         |}
         |object Usage {
         |  new Bar().$Caret$methodUsageText
         |}""".stripMargin
    val after =
      s"""class Bar {
         |  def someOtherMethod = 42
         |
         |  $methodDefinitionText
         |}
         |object Usage {
         |  new Bar().$methodUsageText
         |}""".stripMargin

    doTest(before, after)
  }

  protected def doTestInSameClass(methodUsageText: String, methodDefinitionText: String): Unit = {
    val before =
      s"""class Foo {
         |  def test = $Caret$methodUsageText
         |
         |  def someOtherMethod = 42
         |}""".stripMargin
    val after =
      s"""class Foo {
         |  def test = $methodUsageText
         |
         |  private $methodDefinitionText
         |
         |  def someOtherMethod = 42
         |}""".stripMargin

    doTest(before, after)
  }

  protected def doTestInSameClassWithThis(methodUsageText: String, methodDefinitionText: String): Unit = {
    val before =
      s"""class Foo {
         |  def test = this.$Caret$methodUsageText
         |
         |  def someOtherMethod = 42
         |}""".stripMargin
    val after =
      s"""class Foo {
         |  def test = this.$methodUsageText
         |
         |  private $methodDefinitionText
         |
         |  def someOtherMethod = 42
         |}""".stripMargin

    doTest(before, after)
  }

  protected def doTestInTopLevel(methodUsageText: String, methodDefinitionText: String): Unit = {
    val before =
      s"""$methodUsageText""".stripMargin
    val after =
      s"""$methodDefinitionText
         |
         |$methodUsageText""".stripMargin

    testQuickFix(before, after, hint)
  }
}

class CreateMethodQuickFixTest extends CreateMethodQuickFixTestBase {
  def testCreateMethod(): Unit = {
    val usage = """foo(42, "text", Some(true))"""
    val definition = """def foo(i: Int, str: String, someBoolean: Some[Boolean]) = ???"""
    doCompoundTest(usage, definition)
  }

  def testCreateMethod_WithNamedArguments_All(): Unit = {
    val usage = """foo(name1 = 42, name2 = "text", name3 = Some(true))"""
    val definition = """def foo(name1: Int, name2: String, name3: Some[Boolean]) = ???"""
    doCompoundTest(usage, definition)
  }

  def testCreateMethod_WithNamedArguments_SomeInTheBeginning(): Unit = {
    val usage = """foo(name1 = 42, name2 = "text", Some(true))"""
    val definition = """def foo(name1: Int, name2: String, someBoolean: Some[Boolean]) = ???"""
    doCompoundTest(usage, definition)
  }

  def testCreateMethod_WithNamedArguments_SomeInTheEnd(): Unit = {
    val usage = """foo(42, name2 = "text", name3 = Some(true))"""
    val definition = """def foo(i: Int, name2: String, name3: Some[Boolean]) = ???"""
    doCompoundTest(usage, definition)
  }

  def testCreateMethod_WithNamedArguments_SomeInTheMiddle(): Unit = {
    val usage = """foo(42, name2 = "text", Some(true))"""
    val definition = """def foo(i: Int, name2: String, someBoolean: Some[Boolean]) = ???"""
    doCompoundTest(usage, definition)
  }

  def testCreateMethod_WithParenthesis(): Unit = {
    val usage = """foo((42))"""
    val definition = """def foo(i: Int) = ???"""
    doCompoundTest(usage, definition)
  }

  private val TopLevelUsage = """foo(42)"""
  private val TopLevelDefinition = """def foo(i: Int) = ???"""

  def testTopLevelFirstElementInFile(): Unit = {
    doTest(
      s"""$TopLevelUsage""".stripMargin,
      s"""$TopLevelDefinition
         |
         |$TopLevelUsage""".stripMargin)
  }

  def testTopLevelFirstElementInFile_1(): Unit = {
    doTest(
      s"""
         |$TopLevelUsage""".stripMargin,
      s"""
         |$TopLevelDefinition
         |
         |$TopLevelUsage""".stripMargin)
  }

  def testTopLevelInTheMiddle(): Unit = {
    doTest(
      s"""val x = 42
         |$TopLevelUsage""".stripMargin,
      s"""val x = 42
         |
         |$TopLevelDefinition
         |
         |$TopLevelUsage""".stripMargin)
  }

  def testTopLevelInTheMiddle_1(): Unit = {
    doTest(
      s"""val x = 42
         |
         |$TopLevelUsage""".stripMargin,
      s"""val x = 42
         |
         |$TopLevelDefinition
         |
         |$TopLevelUsage""".stripMargin)
  }
}

class CreateMethodQuickFixWithScalafmtTest
  extends CreateMethodQuickFixTestBase
    with ScalaFmtForTestsSetupOps {

  override protected def scalafmtConfigsBasePath: String = TestUtils.getTestDataPath + "/annotator/createFromUsage"

  override def setUp(): Unit = {
    super.setUp()

    ScalaFmtForTestsSetupOps.ensureDownloaded(
      ScalafmtVersion.parse("3.7.15").get,
    )
  }

  def testDisabledUseIntellijFormatterForRangeFormatting(): Unit = {
    setScalafmtConfig("scala2_scalafmt.config")
    getScalaCodeStyleSettings.SCALAFMT_USE_INTELLIJ_FORMATTER_FOR_RANGE_FORMAT = false

    //NOTE: we use non-standard indentation to ensure that the scalafmt configuration is used
    doTest(
      s"""object test {
         |     def outer(): Unit = {
         |          def inner() =
         |               for {
         |                    a <- Option(1)
         |                    b <- ${CARET}foo(a, 2)
         |               } yield b
         |     }
         |}
         |""".stripMargin,
      s"""object test {
         |     def outer(): Unit = {
         |          def inner() =
         |               for {
         |                    a <- Option(1)
         |                    b <- foo(a, 2)
         |               } yield b
         |     }
         |
         |     private def foo(a: Int, i: Int) = ???
         |}
         |""".stripMargin
    )
  }
}