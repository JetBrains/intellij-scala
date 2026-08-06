package org.jetbrains.plugins.scala.lang.completion3.command

import com.intellij.codeInsight.lookup.LookupElement
import org.junit.Test

final class ScalaRenameCommandCompletionTest extends ScalaCommandCompletionTestBase {
  private val RenameCommandCompletionPredicate: LookupElement => Boolean = lookupStringStartsWith(_, "Rename")

  private def doRenameCommandCompletionTest(fileText: String): Unit =
    doCommandCompletionTest(fileText, RenameCommandCompletionPredicate)

  private def checkNoRenameCommandCompletion(fileText: String): Unit =
    checkNoCommandCompletion(fileText, RenameCommandCompletionPredicate)

  @Test
  def renameParameter(): Unit = doRenameCommandCompletionTest(
    fileText =
      s"""object Test {
          |  def foo(${start}a$end.$CARET: Int): Unit = {}
          |}""".stripMargin
  )

  // `..` and `.` prefixes should trigger completion
  @Test
  def renameParameterWithFullDotPrefix(): Unit = doRenameCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def foo(${start}a$end..$CARET: Int): Unit = {}
         |}""".stripMargin
  )

  @Test
  def noCompletionWithoutDotPrefix(): Unit = checkNoRenameCommandCompletion(
    fileText =
      s"""object Test {
         |  def foo(a$CARET: Int): Unit = {}
         |}""".stripMargin
  )

  @Test
  def renameTypeParameterFromClause(): Unit = doRenameCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def foo[${start}T$end.$CARET](t: T): T = ???
         |}""".stripMargin
  )

  @Test
  def renameTypeParameterFromParamType(): Unit = doRenameCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def foo[${start}T$end](t: T.$CARET): T = ???
         |}""".stripMargin
  )

  @Test
  def renameTypeParameterFromReturnType(): Unit = doRenameCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def foo[${start}T$end](t: T): T.$CARET = ???
         |}""".stripMargin
  )

  @Test
  def renameMethodAtName(): Unit = doRenameCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def ${start}foo$end.$CARET(a: Int): Unit = {}
         |}""".stripMargin
  )

  @Test
  def renameMethodAtTypeParamClause(): Unit = doRenameCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def ${start}foo$end[T].$CARET(t: T): T = ???
         |}""".stripMargin
  )

  @Test
  def renameMethodAtParamClause(): Unit = doRenameCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def ${start}foo$end(a: Int).$CARET: Unit = {}
         |}""".stripMargin
  )

  @Test
  def renameMethodBetweenParamClauses(): Unit = doRenameCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def ${start}foo$end(a: Int).$CARET(b: Int): Unit = {}
         |}""".stripMargin
  )

  @Test
  def renameMethodAtLastParamClause(): Unit = doRenameCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def ${start}foo$end(a: Int)(b: Int).$CARET: Int = {}
         |}""".stripMargin
  )

  @Test
  def renameMethodAtBodyRightBrace(): Unit = doRenameCommandCompletionTest(
    fileText =
      s"""object Test {
         |  def ${start}foo$end(a: Int): Int = {
         |    a
         |  }.$CARET
         |}""".stripMargin
  )

  @Test
  def noRenameMethodForOneLinerWithoutBraces(): Unit = checkNoRenameCommandCompletion(
    fileText =
      s"""object Test {
         |  def foo(a: Int): Int = a + 1.$CARET
         |}""".stripMargin
  )

  @Test
  def noRenameMethodForOneLinerWithoutBraces2(): Unit = checkNoRenameCommandCompletion(
    fileText =
      s"""object Test {
         |  def foo(a: Int): Int =
         |    a + 1.$CARET
         |}""".stripMargin
  )

  @Test
  def renameMethodDeclarationInTrait(): Unit = doRenameCommandCompletionTest(
    fileText =
      s"""trait Test {
         |  def ${start}foo$end(a: Int).$CARET: Int
         |}""".stripMargin
  )

  @Test
  def renameClassAtName(): Unit = doRenameCommandCompletionTest(
    fileText =
      s"""class ${start}Foo$end.$CARET {
         |}""".stripMargin
  )

  @Test
  def renameClassAtTypeParamClause(): Unit = doRenameCommandCompletionTest(
    fileText =
      s"""class ${start}Foo$end[T].$CARET {
         |}""".stripMargin
  )

  @Test
  def renameClassAtParamClause(): Unit = doRenameCommandCompletionTest(
    fileText =
      s"""class ${start}Foo$end(val a: Int).$CARET {
         |}""".stripMargin
  )

  @Test
  def renameClassBetweenParamClauses(): Unit = doRenameCommandCompletionTest(
    fileText =
      s"""class ${start}Foo$end(val a: Int).$CARET(val b: String) {
         |}""".stripMargin
  )

  @Test
  def renameClassAtLastParamClause(): Unit = doRenameCommandCompletionTest(
    fileText =
      s"""class ${start}Foo$end(val a: Int)(val b: String).$CARET {
         |}""".stripMargin
  )

  @Test
  def renameClassWithTypeParamAtParamClause(): Unit = doRenameCommandCompletionTest(
    fileText =
      s"""class ${start}Foo$end[T](val a: T).$CARET {
         |}""".stripMargin
  )

  @Test
  def renameClassAtBodyRightBrace(): Unit = doRenameCommandCompletionTest(
    fileText =
      s"""class ${start}Foo$end {
         |}.$CARET""".stripMargin
  )

  @Test
  def noRenameClassAtLiteralInsideBody(): Unit = checkNoRenameCommandCompletion(
    fileText =
      s"""class Foo {
         |  val x = 1.$CARET
         |}""".stripMargin
  )

  @Test
  def renameVariable(): Unit = doRenameCommandCompletionTest(
    fileText =
      s"""object Test {
         |  val ${start}x$end.$CARET = 1
         |}""".stripMargin
  )

  @Test
  def renameTypeAlias(): Unit = doRenameCommandCompletionTest(
    fileText =
      s"""object Test {
         |  type ${start}A$end.$CARET = Int
         |}""".stripMargin
  )
}
