package org.jetbrains.plugins.scala.uast.platform_inspections

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.execution.junit.codeInspection.JUnitMalformedDeclarationInspection
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.uast.platform_inspections.JUnitMalformedDeclarationInspectionTest.HighlightInfoShort
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals

import scala.jdk.CollectionConverters.ListHasAsScala

class JUnitMalformedDeclarationInspectionTest extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[JUnitMalformedDeclarationInspection]

  override protected def description: String = null

  override protected def descriptionMatches(s: String): Boolean = s != null

  override protected def additionalLibraries: Seq[LibraryLoader] = Seq(
    IvyManagedLoader("junit" % "junit" % "4.13.2")
  )

  //SCL-20906
  def testMethodShouldBePublicNonStaticHaveNoParametersAndOfTypeVoid(): Unit = {
    val scalaFileText =
      """import junit.framework.TestCase
        |
        |class MyScalaTest extends TestCase {
        |  def testMethod1(): Unit = {}
        |  private def testMethod2(p: String): Unit = {}
        |  protected def testMethod3(p: String): Unit = {}
        |
        |  protected def testMethod4(): Unit = {}
        |  private def testMethod5(): Unit = {}
        |  def testMethod6(p: String): Unit = {}
        |
        |  private[this] def testMethodThis1(): Unit = {}
        |  private[this] def testMethodThis2(p: String): Unit = {}
        |}
        |
        |""".stripMargin

    val javaFileText =
      """import junit.framework.TestCase;
        |
        |public class MyJavaTest extends TestCase {
        |    public void testMethod1() {}
        |    private void testMethod2(String p) {}
        |    protected void testMethod3(String p) {}
        |
        |    protected void testMethod4() {}
        |    private void testMethod5() {}
        |    public void testMethod6(String p) {}
        |}
        |""".stripMargin

    val expectedHighlightsJava = Seq[HighlightInfoShort](
      HighlightInfoShort(HighlightSeverity.ERROR, "testMethod4", "Method 'testMethod4' should be public, non-static, have no parameters and of type void"),
      HighlightInfoShort(HighlightSeverity.ERROR, "testMethod5", "Method 'testMethod5' should be public, non-static, have no parameters and of type void"),
      HighlightInfoShort(HighlightSeverity.ERROR, "testMethod6", "Method 'testMethod6' should be public, non-static, have no parameters and of type void"),
    )

    val expectedHighlightsScala = expectedHighlightsJava :+
      HighlightInfoShort(HighlightSeverity.ERROR, "testMethodThis1", "Method 'testMethodThis1' should be public, non-static, have no parameters and of type void")

    myFixture.configureByText("a.scala", scalaFileText)
    val highlightingInfosScala = myFixture.doHighlighting().asScala.toSeq
    val actualWarningsAndErrorsScala = filterWarningsAndErrors(highlightingInfosScala)
    assertCollectionEquals(expectedHighlightsScala, actualWarningsAndErrorsScala)

    //just in case check that java version has same warnings
    myFixture.configureByText("MyJavaTest.java", javaFileText)
    val highlightingInfosJava = myFixture.doHighlighting().asScala.toSeq
    val actualWarningsAndErrorsJava = filterWarningsAndErrors(highlightingInfosJava)
    assertCollectionEquals(expectedHighlightsJava, actualWarningsAndErrorsJava)
  }

  private def filterWarningsAndErrors(highlightingInfos: Seq[HighlightInfo]): Seq[HighlightInfoShort] =
    highlightingInfos
      .filter { info =>
        StringUtil.isNotEmpty(info.getDescription) && (info.getSeverity match {
          case HighlightSeverity.WARNING | HighlightSeverity.ERROR => true // also capture errors just in case
          case _ => false
        })
      }
      .sortBy(_.getStartOffset)
      .map(HighlightInfoShort.apply)
}

object JUnitMalformedDeclarationInspectionTest {

  private case class HighlightInfoShort(level: HighlightSeverity, text: String, description: String)

  private object HighlightInfoShort {
    def apply(info: HighlightInfo): HighlightInfoShort = {
      HighlightInfoShort(info.getSeverity, info.getText, info.getDescription)
    }
  }
}
