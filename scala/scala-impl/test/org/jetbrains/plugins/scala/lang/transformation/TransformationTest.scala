package org.jetbrains.plugins.scala
package lang
package transformation

import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.{PsiElement, PsiFile, PsiFileFactory}
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.junit.Assert.assertEquals

/**
  * @author Pavel Fatin
  */
abstract class TransformationTest extends ScalaLightCodeInsightFixtureTestAdapter {
  @Language("Scala")
  protected val header: String = ""

  import TransformationTest._

  protected def transform(element: PsiElement): Unit

  protected def check(@Language("Scala") before: String,
                      @Language("Scala") after: String)
                     (@Language("Scala") header: String = "",
                      @Language("Scala") footer: String = ""): Unit = {
    implicit val headerAndFooter = (createHeader(header), footer)

    val actualFile = configureByText(before)

    actualFile.depthFirst()
      .foreach(transform)
    assertEquals(after.trim, slice(actualFile).trim)

    val expectedFile = configureByText(after)
    assertEquals(psiToString(expectedFile), psiToString(actualFile))
  }

  private def createHeader(header: String) =
    s"""$PredefinedHeader
       |${this.header}
       |$header""".stripMargin

  private def configureByText(text: String)
                             (implicit headerAndFooter: (String, String)): ScalaFile = {
    val (header, footer) = headerAndFooter
    val fileText =
      s"""$header
         |$text
         |$footer""".stripMargin

    val factory = PsiFileFactory.getInstance(getProject)
    factory.createFileFromText("foo.scala", ScalaFileType.INSTANCE, fileText) match {
      case file: ScalaFile => file
    }
  }
}

object TransformationTest {
  val ScalaSourceHeader = "import scala.io.Source"

  private val PredefinedHeader =
    s"""class A { def a(): Unit = _ }
       |class B { def b(): Unit = _ }
       |class C { def c(): Unit = _ }
       |object A extends A
       |object B extends B
       |object C extends C""".stripMargin

  private def psiToString(file: PsiFile): String =
    DebugUtil.psiToString(file, false)

  private def slice(file: PsiFile)
                   (implicit headerAndFooter: (String, String)): String = {
    val (header, footer) = headerAndFooter
    val text = file.getText
    text.substring(header.length + 1, text.length - (footer.length + 1))
  }
}

abstract class TransformerTest(private val transformer: Transformer) extends TransformationTest {
  override protected final def transform(element: PsiElement): Unit =
    transformer.transform(element)
}
