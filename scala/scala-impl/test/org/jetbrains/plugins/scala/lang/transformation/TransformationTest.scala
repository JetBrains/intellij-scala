package org.jetbrains.plugins.scala.lang.transformation

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions._
import org.junit.Assert.assertEquals

abstract class TransformationTest extends ScalaLightCodeInsightFixtureTestCase {
  @Language("Scala")
  protected val header: String = ""

  import TransformationTest._

  protected def transform(element: PsiElement, file : PsiFile, reformat: Transformer.ReformatAction): Unit

  protected final def check(@Language("Scala") before: String,
                            @Language("Scala") after: String)
                           (@Language("Scala") header: String = "",
                            @Language("Scala") footer: String = ""): Unit = {
    doCheck(
      before.withNormalizedSeparator,
      after.withNormalizedSeparator
    )(
      header.withNormalizedSeparator,
      footer.withNormalizedSeparator
    )
  }

  private def doCheck(@Language("Scala") before: String,
                      @Language("Scala") after: String)
                     (@Language("Scala") header: String,
                      @Language("Scala") footer: String): Unit = {
    implicit val headerAndFooter: (String, String) = (createHeader(header), footer)

    val actualFile = configureByText(before)

    // collect all ranges that should be formatted
    var actualRewriteTextRanges = List.empty[TextRange]
    val reformat: Transformer.ReformatAction = (textRanges, _, _) => actualRewriteTextRanges :::= textRanges

    inWriteAction {
      implicit val p: Project = getProject
      startCommand("test command") {
        //NOTE: currently we apply transformation to all elements in the file
        //Ideally we should do it only for the tested code, without header and footer
        actualFile
          .depthFirst()
          .filter(_.isValid) //some elements can become invalid after transform was performed on it's parents
          .foreach { element =>
            transform(element, actualFile, reformat)
          }

        if (actualRewriteTextRanges.nonEmpty) {
          Transformer.defaultReformat(actualRewriteTextRanges, getFile, getEditor.getDocument)
        }
      }
    }

    assertEquals(after.trim, slice(actualFile).trim)

    val expectedFile = configureByText(after)
    assertEquals(psiToString(expectedFile), psiToString(actualFile))
  }

  private def createHeader(header: String) =
    s"""$PredefinedHeader
       |${this.header}
       |$header""".stripMargin.withNormalizedSeparator

  private def configureByText(text: String)
                             (implicit headerAndFooter: (String, String)): PsiFile = {
    val (header, footer) = headerAndFooter
    val fileText =
      s"""$header
         |$text
         |$footer""".stripMargin.withNormalizedSeparator

    myFixture.configureByText("foo.scala", fileText)
    myFixture.getFile
  }
}

object TransformationTest {
  val ScalaSourceHeader = "import scala.io.Source"

  private val PredefinedHeader: String =
    s"""class A { def a(): Unit = _ }
       |class B { def b(): Unit = _ }
       |class C { def c(): Unit = _ }
       |object A extends A
       |object B extends B
       |object C extends C""".stripMargin.withNormalizedSeparator

  private def psiToString(file: PsiFile): String =
    DebugUtil.psiToString(file, true)

  private def slice(file: PsiFile)
                   (implicit headerAndFooter: (String, String)): String = {
    val (header, footer) = headerAndFooter
    val text = file.getText
    text.substring(header.length + 1, text.length - (footer.length + 1))
  }
}

abstract class TransformerTest(private val transformer: Transformer) extends TransformationTest {
  override protected final def transform(element: PsiElement, file: PsiFile, reformat: Transformer.ReformatAction): Unit =
    Transformer.applyTransformerAndReformat(element, file, transformer, reformat)
}
