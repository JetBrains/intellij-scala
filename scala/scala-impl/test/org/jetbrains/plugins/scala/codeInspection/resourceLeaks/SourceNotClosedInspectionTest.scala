package org.jetbrains.plugins.scala.codeInspection.resourceLeaks

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}

class SourceNotClosedInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[SourceNotClosedInspection]

  override protected val description: String = ScalaInspectionBundle.message("source.not.closed")

  def checkHasError(text: String): Unit = {
    checkTextHasError(
      s"""
         |import scala.io.Source
         |import java.io.File
         |import java.net.{URI, URL}
         |
         |object TestMain {
         |  def consume(something: Any): Any = ???
         |  def test() {
         |    $text
         |  }
         |}
      """.stripMargin)
  }

  def testFromFileMkString(): Unit =
    checkHasError(s"""${START}Source.fromFile(new File("test")).mkString$END""")

  def testFromURIMkString(): Unit =
    checkHasError(s"""${START}Source.fromURI(new URI("test")).mkString$END""")

  def testFromURLMkString(): Unit =
    checkHasError(s"""${START}Source.fromURL(new URL("test")).mkString$END""")

  def testFromFileMkStringSep(): Unit =
    checkHasError(s"""${START}Source.fromFile(new File("test")).mkString(",")$END""")

  def testFromURIMkStringSep(): Unit =
    checkHasError(s"""${START}Source.fromURI(new URI("test")).mkString(",")$END""")

  def testFromURLMkStringSep(): Unit =
    checkHasError(s"""${START}Source.fromURL(new URL("test")).mkString(",")$END""")

  def testFromFileGetLines(): Unit =
    checkHasError(s"""${START}Source.fromFile(new File("test")).getLines()$END""")

  def testFromURIGetLines(): Unit =
    checkHasError(s"""${START}Source.fromURI(new URI("test")).getLines()$END""")

  def testFromURLGetLines(): Unit =
    checkHasError(s"""${START}Source.fromURL(new URL("test")).getLines()$END""")

  def testSourceFromFileMkString(): Unit = checkHasError(
    s"""
       |val file = new File("test")
       |
       |${START}Source.fromFile(file).mkString$END
       |
     """.stripMargin
  )

  def testUnqualifiedFromFileMkString(): Unit = checkHasError(
    s"""
       |import Source.fromFile
       |val file = new File("test")
       |
       |${START}fromFile(file).mkString$END
       |
       """.stripMargin
  )

  def testQualifiedRenamedFromFileMkString(): Unit = checkHasError(
    s"""
       |import scala.io.{Source => S}
       |
       |${START}S.fromFile(new java.io.File("test")).mkString$END
       |
     """.stripMargin
  )

  def testCompletelyUnusedFromFile(): Unit = checkHasError(
    s"""
       |${START}Source.fromFile(new File("test"))$END
     """.stripMargin
  )

  def testAssignedSource(): Unit = checkTextHasNoErrors(
    """
      |val x = Source.fromFile(new File("test")))
    """.stripMargin
  )


  def testConsumingExtensionMethod(): Unit = checkTextHasNoErrors(
    """
      |implicit class SourceExt(val source: Source) extends AnyVal {
      |  def useIn[T](body: => T): T = ???
      |}
      |
      |Source.fromFile(new File("test")).useIn { file =>
      |  file.mkString
      |}
    """.stripMargin
  )

  def testInfixMkString(): Unit = checkHasError(
    s"""
      |${START}Source.fromFile(new File("test")) mkString ","$END
    """.stripMargin
  )

  def testInfixFold(): Unit = checkHasError(
    s"""
       |($START"Begin: " /: Source.fromFile(new File("test"))$END) { _ + _ }
     """.stripMargin
  )

  def testLength(): Unit = checkHasError(
    s"""
       |${START}Source.fromFile(new File("test"))$END
     """.stripMargin
  )

  def testConsumed(): Unit = checkTextHasNoErrors(
    s"""
       |consume(Source.fromURL(new URL("test")))
     """.stripMargin
  )

  def testInfixConsumed(): Unit = checkTextHasNoErrors(
    s"""
       |Source.fromURL(new URL("test")) :: Nil
     """.stripMargin
  )

  def testImmedeatelyClosed(): Unit = checkTextHasNoErrors(
    """
      |Source.fromURL(new URL("test")).closed()
    """.stripMargin
  )

  // tests by mattfowler
  def testFromFileShouldShowErrors(): Unit = {
    checkHasError(
      s"""
         |${START}Source.fromFile("filename").getLines()$END
       """.stripMargin
    )

    checkHasError(
      s"""
         |${START}Source.fromFile("filename").mkString$END
       """.stripMargin
    )

    checkHasError(
      s"""
         |${START}Source.fromFile("filename", "encoding").getLines()$END
       """.stripMargin
    )

    checkHasError(
      s"""
         |${START}Source.fromFile("filename", "encoding").mkString$END
       """.stripMargin
    )

    checkHasError(
      s"""
         |${START}Source.fromFile(new File("filename"), "encoding").getLines()$END
       """.stripMargin
    )
    checkHasError(
      s"""
         |${START}Source.fromFile(new File("filename"), "encoding").mkString$END
       """.stripMargin
    )
  }

  def testFromURLShouldShowErrors(): Unit = {
    checkHasError(
      s"""
         |${START}Source.fromURL(new URL("url"), "encoding").getLines()$END
       """.stripMargin
    )
    checkHasError(
      s"""
         |${START}Source.fromURL(new URL("url"), "encoding").mkString$END
       """.stripMargin
    )
  }

  def testFromURIShouldShowErrors(): Unit = {
    checkHasError(
      s"""
         |${START}Source.fromURI(new URI("uri")).getLines()$END
       """.stripMargin
    )
    checkHasError(
      s"""
         |${START}Source.fromURI(new URI("uri")).mkString$END
       """.stripMargin
    )
  }

  // TODO: this is currently pending because the InvocationTemplate tests do not check for renames
  /*def testdRenamedFromFileMkString(): Unit =
    checkHasError(
      s"""import scala.io.Source.{fromFile => gotcha}
         |${START}gotcha(new java.io.File("test")).mkString$END
         |""".stripMargin)*/
}
