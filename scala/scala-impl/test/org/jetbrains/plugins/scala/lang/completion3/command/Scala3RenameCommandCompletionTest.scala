package org.jetbrains.plugins.scala.lang.completion3.command

import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}
import org.junit.Test

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
final class Scala3RenameCommandCompletionTest extends ScalaCommandCompletionTestBase {
  private val RenameCommandCompletionPredicate: LookupElement => Boolean = lookupStringStartsWith(_, "Rename")

  private def doRenameCommandCompletionTest(fileText: String): Unit =
    doCommandCompletionTest(fileText, RenameCommandCompletionPredicate)

  @Test
  def renameTypeParameterFromInterleavedClause(): Unit = doRenameCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def foo[A](a: A)[${start}B$end.$CARET](b: B): Unit = ???
         |}""".stripMargin
  )

  @Test
  def renameMethodAtInterleavedTypeParamClause(): Unit = doRenameCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def ${start}foo$end[A](a: A)[B].$CARET(b: B): Unit = ???
         |}""".stripMargin
  )
}
