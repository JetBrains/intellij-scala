package org.jetbrains.plugins.scala.codeInspection.resourceLeaks

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.ScalaQuickFixTestBase

class SourceNotClosedInspectionTest extends ScalaQuickFixTestBase {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[SourceNotClosedInspection]

  override protected val description: String = ScalaBundle.message("source.not.closed")

  def testFullyQualifiedFromFileMkString(): Unit =
    checkTextHasError(s"""${START}scala.io.Source.fromFile(new java.io.File("test")).mkString$END""")
  def testFullyQualifiedFromURIMkString(): Unit =
    checkTextHasError(s"""${START}scala.io.Source.fromURI(new java.net.URI("test")).mkString$END""")
  def testFullyQualifiedFromURLMkString(): Unit =
    checkTextHasError(s"""${START}scala.io.Source.fromURL(new java.net.URL("test")).mkString$END""")

  def testFullyQualifiedFromFileMkStringSep(): Unit =
    checkTextHasError(s"""${START}scala.io.Source.fromFile(new java.io.File("test")).mkString(",")$END""")
  def testFullyQualifiedFromURIMkStringSep(): Unit =
    checkTextHasError(s"""${START}scala.io.Source.fromURI(new java.net.URI("test")).mkString(",")$END""")
  def testFullyQualifiedFromURLMkStringSep(): Unit =
    checkTextHasError(s"""${START}scala.io.Source.fromURL(new java.net.URL("test")).mkString(",")$END""")

  def testFullyQualifiedFromFileGetLines(): Unit =
    checkTextHasError(s"""${START}scala.io.Source.fromFile(new java.io.File("test")).getLines()$END""")
  def testFullyQualifiedFromURIGetLines(): Unit =
    checkTextHasError(s"""${START}scala.io.Source.fromURI(new java.net.URI("test")).getLines()$END""")
  def testFullyQualifiedFromURLGetLines(): Unit =
    checkTextHasError(s"""${START}scala.io.Source.fromURL(new java.net.URL("test")).getLines()$END""")

  def testSourceFromFileMkString(): Unit =
    checkTextHasError(
      s"""import scala.io.Source
         |import java.io.File
         |
         |val file = new File("test")
         |
         |${START}Source.fromFile(file).mkString$END
         |""".stripMargin)

  def testUnqualifiedFromFileMkString(): Unit =
    checkTextHasError(
      s"""import scala.io.Source.fromFile
         |import java.io.File
         |
         |val file = new File("test")
         |
         |${START}fromFile(file).mkString$END
         |""".stripMargin)

  def testQualifiedRenamedFromFileMkString(): Unit =
    checkTextHasError(
      s"""import scala.io.{Source => S}
         |${START}S.fromFile(new java.io.File("test")).mkString$END
         |""".stripMargin)

  // TODO: this is currently pending because the InvocationTemplate tests do not check for renames
  def pendingUnqualifiedRenamedFromFileMkString(): Unit =
    checkTextHasError(
      s"""import scala.io.Source.{fromFile => gotcha}
         |${START}gotcha(new java.io.File("test")).mkString$END
         |""".stripMargin)
}
