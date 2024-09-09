package org.jetbrains.plugins.scala.lang.typeInference

import com.intellij.psi.PsiFile
import junit.framework.TestCase
import org.jetbrains.plugins.scala.TypecheckerTests
import org.jetbrains.plugins.scala.annotator.Message
import org.jetbrains.plugins.scala.base.{FailableTest, ScalaSdkOwner}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.junit.Assert._
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
trait TypeInferenceDoTest extends TestCase with FailableTest with ScalaSdkOwner {
  protected val typeInferenceFixture = new TypeInferenceTestFixture(
    version,
    shouldPass = shouldPass
  )

  protected val START_MARKER = typeInferenceFixture.START_MARKER
  protected val END_MARKER = typeInferenceFixture.END_MARKER

  def configureFromFileText(fileName: String, fileText: Option[String]): ScalaFile

  protected def errorsFromAnnotator(file: PsiFile): Seq[Message.Error]

  final protected def doTest(fileText: String): Unit = {
    doTest(fileText, failIfNoAnnotatorErrorsInFileIfTestIsSupposedToFail = true)
  }

  final protected def doTest(
    fileText: String,
    failIfNoAnnotatorErrorsInFileIfTestIsSupposedToFail: Boolean
  ): Unit = {
    doTest(Some(fileText), failIfNoAnnotatorErrorsInFileIfTestIsSupposedToFail = failIfNoAnnotatorErrorsInFileIfTestIsSupposedToFail)
  }

  protected def findSelectedExpression(scalaFile: ScalaFile): ScExpression =
    typeInferenceFixture.findSelectedExpression(scalaFile)

  protected def doTest(
    fileText: Option[String],
    failOnParserErrorsInFile: Boolean = true,
    failIfNoAnnotatorErrorsInFileIfTestIsSupposedToFail: Boolean = true,
    fileName: String = "dummy.scala"
  ): Unit = {
    val scalaFile: ScalaFile = configureFromFileText(fileName, fileText)

    if (!shouldPass && failIfNoAnnotatorErrorsInFileIfTestIsSupposedToFail) {
      val errors = errorsFromAnnotator(scalaFile)
      assertTrue(
        s"""Expected to detect annotator errors in available test, but found no errors.
           |Maybe the test was fixed in some changes?
           |Check it manually and consider moving into successfully tests.
           |If the test is still actual but no annotator errors is expected then pass argument `failIfNoAnnotatorErrorsInFileIfTestIsSupposedToFail=true`""".stripMargin,
        errors.nonEmpty
      )
    }

    typeInferenceFixture.doTest(scalaFile, failOnParserErrorsInFile = failOnParserErrorsInFile)
  }
}
