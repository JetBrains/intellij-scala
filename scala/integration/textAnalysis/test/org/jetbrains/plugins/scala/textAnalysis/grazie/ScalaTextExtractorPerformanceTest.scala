package org.jetbrains.plugins.scala.textAnalysis.grazie

import com.intellij.grazie.text.{TextContent, TextContentBuilder}
import com.intellij.psi.PsiComment
import com.intellij.psi.javadoc.{PsiDocComment, PsiDocTag}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.UsefulTestCase.assertInstanceOf
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.util.ThrowableRunnable
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.junit.Assert.assertEquals

import scala.jdk.CollectionConverters.ListHasAsScala

class ScalaTextExtractorPerformanceTest extends BasePlatformTestCase {

  protected final def runPerformanceTest(launchName: String)(runnable: ThrowableRunnable[?]): Unit =
    Benchmark.newBenchmark(launchName, runnable).start()

  // Annotation added within MRI-3451
  @PerformanceUnitTest
  def testBuildingPerformance_removingIndents(): Unit = {
    val text = "  b\n".repeat(10_000)
    val expected = "b\n".repeat(10_000).trim

    val file = myFixture.configureByText("dummy.scala", s"/*\n$text*/")

    val comment = assertInstanceOf(file.findElementAt(10), classOf[PsiComment])
    val builder = TextContentBuilder.FromPsi.removingIndents(" ")

    runPerformanceTest("TextContent building with indent removing") { () =>
      assertEquals(expected, builder.build(comment, TextContent.TextDomain.COMMENTS).toString)
    }
  }

  @PerformanceUnitTest
  def testBuildingPerformance_removingHtml(): Unit = {
    val text = "b<unknownTag>x</unknownTag>".repeat(10_000)
    val expected = List("b".repeat(10_000))
    val file = myFixture.configureByText("dummy.scala", "/**\n" + text + "*/")
    val comment = PsiTreeUtil.findElementOfClassAtOffset(file, 10, classOf[PsiDocComment], false)
    val extractor = new ScalaTextExtractor
    runPerformanceTest("TextContent building with HTML removal") { () =>
      assertEquals(expected, extractor.buildTextContents(comment, TextContent.TextDomain.ALL).asScala.toList.map(_.toString))
    }
  }

  @PerformanceUnitTest
  def testBuildingPerformance_longTextFragment(): Unit = {
    val line = "here's some relative long text that helps make this text fragment a bit longer than it could have been otherwise"
    val text = ("\n\n\n" + line).repeat(10_000)
    val expected = List((line + "\n\n\n").repeat(10_000).trim)
    val file = myFixture.configureByText("dummy.scala", "class C { String s = \"\"\"\n" + text + "\"\"\"; }")
    val literal = PsiTreeUtil.findElementOfClassAtOffset(file, 100, classOf[ScStringLiteral], false)
    val extractor = new ScalaTextExtractor
    runPerformanceTest("TextContent building from a long text fragment") { () =>
      assertEquals(expected, extractor.buildTextContents(literal, TextContent.TextDomain.ALL).asScala.toList.map(_.toString))
    }
  }

  @PerformanceUnitTest
  def testBuildingPerformance_ComplexScalaDocPsi(): Unit = {
    val link = "[[foo.bar.goo1.goo2.goo3.goo4.goo5.goo6.goo7]]"
    val text = "/** @return something if " + link.repeat(10_000) + " is not too expensive */"
    val file = myFixture.configureByText("dummy.scala", text)
    val extractor = new ScalaTextExtractor
    val tag = PsiTreeUtil.findElementOfClassAtOffset(file, text.indexOf("something"), classOf[PsiDocTag], false)
    runPerformanceTest("TextContent building from complex PSI") { () =>
      for (_ <- 0 until 10) {
        val content = extractor.buildTextContents(tag, TextContent.TextDomain.ALL).asScala.toList
        assertEquals(List("something if  is not too expensive"), content.map(_.toString))
      }
    }
  }
}
