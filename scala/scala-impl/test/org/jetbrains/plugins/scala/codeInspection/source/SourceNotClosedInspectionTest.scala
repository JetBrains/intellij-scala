package org.jetbrains.plugins.scala.codeInspection.source

import com.intellij.testFramework.EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

class SourceNotClosedInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection = classOf[SourceNotClosedInspection]
  override protected val description = "Source not closed"

  private val SOURCE_IMPORT = "import scala.io.{Codec, Source} import java.io.{File, FileInputStream} import java.net.{URI, URL}"

  def testFromFileShouldShowErrors(): Unit = {
    checkTextHasError(s"""$SOURCE_IMPORT ${START}Source.fromFile("filename").getLines()$END""")
    checkTextHasError(s"""$SOURCE_IMPORT ${START}Source.fromFile("filename").mkString$END""")

    checkTextHasError(s"""$SOURCE_IMPORT ${START}Source.fromFile("filename", "encoding").getLines()$END""")
    checkTextHasError(s"""$SOURCE_IMPORT ${START}Source.fromFile("filename", "encoding").mkString$END""")

    checkTextHasError(s"""$SOURCE_IMPORT ${START}Source.fromFile(new File("filename"), "encoding").getLines()$END""")
    checkTextHasError(s"""$SOURCE_IMPORT ${START}Source.fromFile(new File("filename"), "encoding").mkString$END""")
  }

  def testFromURLShouldShowErrors(): Unit = {
    checkTextHasError(s"""$SOURCE_IMPORT ${START}Source.fromURL(new URL("url"), "encoding").getLines()$END""")
    checkTextHasError(s"""$SOURCE_IMPORT ${START}Source.fromURL(new URL("url"), "encoding").mkString$END""")
  }

  def testFromURIShouldShowErrors(): Unit = {
    checkTextHasError(s"""$SOURCE_IMPORT ${START}Source.fromURI(new URI("uri")).getLines()$END""")
    checkTextHasError(s"""$SOURCE_IMPORT ${START}Source.fromURI(new URI("uri")).mkString$END""")
  }

  def testFromInputStreamShouldShowErrorsIfStreamCreatedLocally(): Unit = {
    checkTextHasError(s"""$SOURCE_IMPORT ${START}Source.fromInputStream(new FileInputStream("file")).getLines()$END""")
    checkTextHasError(s"""$SOURCE_IMPORT ${START}Source.fromInputStream(new FileInputStream("file")).mkString$END""")
  }

  def testFromInputStreamShouldShowNoErrorsIfStreamNotCreatedLocally(): Unit = {
    checkTextHasNoErrors(
      s"""
         |$SOURCE_IMPORT
         | val is = new FileInputStream("file")
         | Source.fromInputStream(file)
         | is.close()
      """.stripMargin)
  }

  def testShouldOnlyShowOnScalaIOSource(): Unit = {
    checkTextHasNoErrors(
      """
        |  object Source {
        |    def fromFile(s: String) = P()
        |  }
        |
        |  case class P() {
        |    def getLines(): Seq[String] = Seq()
        |  }
        |
        |  Source.fromFile("filename").getLines()
      """.stripMargin)
  }
}
