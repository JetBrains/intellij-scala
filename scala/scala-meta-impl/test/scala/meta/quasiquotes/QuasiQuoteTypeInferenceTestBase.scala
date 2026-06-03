package scala.meta.quasiquotes

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

import scala.meta.ScalaMetaTestBase

abstract class QuasiQuoteTypeInferenceTestBase extends TypeInferenceTestBase with ScalaMetaTestBase {

  override protected def doTest(
    fileText: Option[String],
    failOnParserErrorsInFile: Boolean,
    failIfNoAnnotatorErrorsInFileIfTestIsSupposedToFail: Boolean,
    fileName: String
  ): Unit = {
    val fileTextUpdated = fileText.map(t =>
      s"""import scala.meta._
         |$t""".stripMargin
    )
    super.doTest(
      fileTextUpdated,
      failOnParserErrorsInFile,
      failIfNoAnnotatorErrorsInFileIfTestIsSupposedToFail,
      fileName
    )
  }

}
