package org.jetbrains.plugins.scala.lang.superMember

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{PathExt, StringExt}
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert._

import java.nio.file.Path

class SuperMemberTest extends ScalaLightCodeInsightFixtureTestCase {
  val CARET_MARKER = "<caret>"

  override protected def sourceRootPath: Path = Path.of(TestUtils.getTestDataPath, "supers")

  private def removeMarker(text: String): (Int, String) = {
    val index = text.indexOf(CARET_MARKER)
    (index, text.substring(0, index) + text.substring(index + CARET_MARKER.length))
  }

  def testToString(): Unit =
    runTest(Path.of("objectMethods", "toString.scala"))

  def testHashCode(): Unit =
    runTest(Path.of("objectMethods", "hashCode.scala"))

  def testTraitSuper(): Unit =
    runTest(Path.of("traits", "traitSuper.scala"))

  def testClassAliasSuper(): Unit =
    runTest(Path.of("class", "ClassAliasDependent.scala"))

  def testSelfType(): Unit =
    runTest(Path.of("selfType", "SelfType.scala"))

  def testJavaBaseWithScalaTraitAndScalaClass(): Unit = {
    myFixture.addFileToProject(
      "JavaBase.java",
      """public class JavaBase {
        |    public int foo() {
        |        return 0;
        |    }
        |}
        |""".stripMargin
    )

    val (scalaTraitOffset, scalaTraitText) = removeMarker(
      """trait ScalaTrait extends JavaBase {
        |  override def <caret>foo = 1
        |}
        |
        |class ScalaClass extends ScalaTrait {
        |  override def foo = 2
        |}
        |""".stripMargin
    )
    val scalaTraitFile = configureFromFileText("ScalaDefinitions.scala", scalaTraitText)
    assertEquals("JavaBase.foo", SuperMethodTestUtil.transform(scalaTraitFile, scalaTraitOffset))

    val (scalaClassOffset, scalaClassText) = removeMarker(
      """trait ScalaTrait extends JavaBase {
        |  override def foo = 1
        |}
        |
        |class ScalaClass extends ScalaTrait {
        |  override def <caret>foo = 2
        |}
        |""".stripMargin
    )
    val scalaClassFile = configureFromFileText("ScalaDefinitions.scala", scalaClassText)
    assertEquals("ScalaTrait.foo\nJavaBase.foo", SuperMethodTestUtil.transform(scalaClassFile, scalaClassOffset))
  }

  private def runTest(subPath: Path): Unit = {
    val fileName = subPath.getFileName.toString
    val filePath = sourceRootPath / subPath
    val (offset, text) = removeMarker(readText(filePath))

    val testFilePath = filePath.getParent / fileName.replaceFirst("[.]scala", ".test")
    val resText = readText(testFilePath)

    val file = configureFromFileText(fileName, text)
    assertEquals(resText, SuperMethodTestUtil.transform(file, offset))
  }

  private def readText(path: Path): String = path.readAllBytesToString().withNormalizedSeparator
}
