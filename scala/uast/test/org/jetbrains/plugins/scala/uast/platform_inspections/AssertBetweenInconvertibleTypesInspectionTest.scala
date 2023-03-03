package org.jetbrains.plugins.scala.uast.platform_inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.test.AssertBetweenInconvertibleTypesInspection
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

class AssertBetweenInconvertibleTypesInspectionTest extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[AssertBetweenInconvertibleTypesInspection]

  override protected val description: String = null

  override protected def descriptionMatches(s: String): Boolean =
    s != null && s.contains("'assertEquals()' between objects of inconvertible types")

  override protected def additionalLibraries: Seq[LibraryLoader] = Seq(
    IvyManagedLoader("junit" % "junit" % "4.13.2")
  )

  def testMethodCalls_NoWarning(): Unit = checkTextHasNoErrors(
    """import org.junit.Assert.assertEquals
      |
      |class MyScalaTest {
      |  val seq: Seq[String] = Seq()
      |  import seq._
      |
      |  assertEquals("", mkString)
      |  assertEquals("", mkString("\n"))
      |}
      |""".stripMargin
  )

  def testMethodCalls_HasWarning(): Unit = checkTextHasError(
    s"""import org.junit.Assert.assertEquals
      |
      |class MyScalaTest {
      |  val seq: Seq[String] = Seq()
      |  import seq._
      |
      |  ${START}assertEquals$END(42, mkString)
      |  ${START}assertEquals$END(42, mkString("\n"))
      |}
      |""".stripMargin
  )

  def testQualifiedMethodCalls_NoWarning(): Unit = checkTextHasNoErrors(
    """import org.junit.Assert.assertEquals
      |
      |import scala.language.postfixOps
      |
      |class MyScalaTest {
      |  val seq: Seq[String] = Seq()
      |
      |  assertEquals("", seq.mkString)
      |  assertEquals("", seq.mkString("\n"))
      |  assertEquals("", seq mkString "\n" substring 0)
      |  assertEquals("", seq.mkString trim)
      |  assertEquals("", seq.mkString("\n").trim)
      |  assertEquals("", seq.mkString("\n").trim())
      |  assertEquals("", seq mkString "\n" trim)
      |}
      |""".stripMargin
  )

  def testQualifiedMethodCalls_HasWarning(): Unit = checkTextHasError(
    s"""import org.junit.Assert.assertEquals
       |
       |import scala.language.postfixOps
       |
       |class MyScalaTest {
       |  val seq: Seq[String] = Seq()
       |
       |  ${START}assertEquals$END(42, seq.mkString)
       |  ${START}assertEquals$END(42, seq.mkString("\n"))
       |  ${START}assertEquals$END(42, seq mkString "\n" substring 0)
       |  ${START}assertEquals$END(42, seq.mkString trim)
       |  ${START}assertEquals$END(42, seq.mkString("\n").trim)
       |  ${START}assertEquals$END(42, seq mkString "\n" trim)
       |}
       |""".stripMargin
  )
}