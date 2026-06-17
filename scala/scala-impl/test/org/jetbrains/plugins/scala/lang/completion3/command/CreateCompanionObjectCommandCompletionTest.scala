package org.jetbrains.plugins.scala.lang.completion3.command

import com.intellij.codeInsight.lookup.LookupElement
import org.junit.Test

final class CreateCompanionObjectCommandCompletionTest extends ScalaCommandCompletionTestBase {
  private val CreateCompanionObjectCommandCompletionPredicate: LookupElement => Boolean = lookupStringStartsWith(_, "Create companion object")

  private def doCreateCompanionObjectCommandCompletionTest(fileText: String, resultText: String): Unit =
    doCommandCompletionTest(
      fileText,
      resultText = resultText,
      predicate = CreateCompanionObjectCommandCompletionPredicate
    )

  private def checkNoCreateCompanionObjectCommandCompletion(fileText: String): Unit =
    checkNoCommandCompletion(fileText, CreateCompanionObjectCommandCompletionPredicate)

  @Test
  def className(): Unit = doCreateCompanionObjectCommandCompletionTest(
    fileText =
      s"""class ${start}Foo$end..$CARET {
         |}""".stripMargin,
    resultText =
      """class Foo {
        |}
        |
        |object Foo {
        |
        |}""".stripMargin
  )

  @Test
  def caseClassName(): Unit = doCreateCompanionObjectCommandCompletionTest(
    fileText =
      s"""case class ${start}User$end..$CARET(name: String)""".stripMargin,
    resultText =
      """case class User(name: String)
        |
        |object User {
        |
        |}""".stripMargin
  )

  @Test
  def traitName(): Unit = doCreateCompanionObjectCommandCompletionTest(
    fileText =
      s"""trait ${start}Service$end..$CARET {
         |  def run(): Unit
         |}""".stripMargin,
    resultText =
      """trait Service {
        |  def run(): Unit
        |}
        |
        |object Service {
        |
        |}""".stripMargin
  )

  @Test
  def classAtConstructorParameterClause(): Unit = doCreateCompanionObjectCommandCompletionTest(
    fileText =
      s"""class ${start}Foo$end(x: Int)..$CARET""".stripMargin,
    resultText =
      """class Foo(x: Int)
        |
        |object Foo {
        |
        |}""".stripMargin
  )

  @Test
  def classAtOpeningBrace(): Unit = doCreateCompanionObjectCommandCompletionTest(
    fileText =
      s"""class ${start}Foo$end {..$CARET
         |  def value: Int = 42
         |}""".stripMargin,
    resultText =
      """class Foo {
        |  def value: Int = 42
        |}
        |
        |object Foo {
        |
        |}""".stripMargin
  )

  @Test
  def classAtClosingBrace(): Unit = doCreateCompanionObjectCommandCompletionTest(
    fileText =
      s"""class ${start}Foo$end {
         |  def value: Int = 42
         |}..$CARET""".stripMargin,
    resultText =
      """class Foo {
        |  def value: Int = 42
        |}
        |
        |object Foo {
        |
        |}""".stripMargin
  )

  @Test
  def noCompletionForClassWithExistingCompanionBelow(): Unit = checkNoCreateCompanionObjectCommandCompletion(
    s"""class Foo..$CARET
       |
       |object Foo""".stripMargin
  )

  @Test
  def noCompletionForClassWithExistingCompanionAbove(): Unit = checkNoCreateCompanionObjectCommandCompletion(
    s"""object Foo
       |
       |class Foo..$CARET""".stripMargin
  )

  @Test
  def noCompletionForObject(): Unit = checkNoCreateCompanionObjectCommandCompletion(
    s"""object Foo..$CARET"""
  )

  @Test
  def noCompletionForMethodName(): Unit = checkNoCreateCompanionObjectCommandCompletion(
    s"""object Test {
       |  def foo..$CARET(): Unit = ()
       |}""".stripMargin
  )

  @Test
  def noCompletionForLiteral(): Unit = checkNoCreateCompanionObjectCommandCompletion(
    s"""object Test {
       |  val value = 42.$CARET
       |}""".stripMargin
  )
}
