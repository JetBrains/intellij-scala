package org.jetbrains.plugins.scala.lang.completion3.command

import com.intellij.codeInsight.lookup.LookupElement
import org.junit.Test

final class ScalaTypeHierarchyCommandCompletionTest extends ScalaCommandCompletionTestBase {
  private val TypeHierarchyPredicate: LookupElement => Boolean = lookupStringStartsWith(_, "type hierarchy")

  private def doTypeHierarchyCommandCompletionTest(fileText: String): Unit =
    doCommandCompletionTest(fileText, predicate = TypeHierarchyPredicate, finishLookup = false)

  private def checkNoTypeHierarchyCommandCompletion(fileText: String): Unit =
    checkNoCommandCompletion(fileText, predicate = TypeHierarchyPredicate)

  @Test
  def className(): Unit = doTypeHierarchyCommandCompletionTest(
    s"""class ${start}MyClass$end..$CARET(val x: Int)"""
  )

  @Test
  def traitName(): Unit = doTypeHierarchyCommandCompletionTest(
    s"""trait ${start}MyTrait$end..$CARET {
       |  def method(): Unit
       |}""".stripMargin
  )

  @Test
  def objectName(): Unit = doTypeHierarchyCommandCompletionTest(
    s"""object ${start}MyObject$end..$CARET {
       |  val value = 1
       |}""".stripMargin
  )

  @Test
  def caseClassName(): Unit = doTypeHierarchyCommandCompletionTest(
    s"""case class ${start}Point$end..$CARET(x: Int, y: Int)"""
  )

  @Test
  def abstractClassName(): Unit = doTypeHierarchyCommandCompletionTest(
    s"""abstract class ${start}AbstractBase$end..$CARET {
       |  def method(): Unit
       |}""".stripMargin
  )

  @Test
  def classReference(): Unit = doTypeHierarchyCommandCompletionTest(
    s"""class MyClass(val x: Int)
       |
       |object Test {
       |  val obj: ${start}MyClass$end..$CARET = new MyClass(1)
       |}""".stripMargin
  )

  @Test
  def classReferenceInNew(): Unit = doTypeHierarchyCommandCompletionTest(
    s"""class MyClass(val x: Int)
       |
       |object Test {
       |  val obj = new ${start}MyClass$end..$CARET(1)
       |}""".stripMargin
  )

  @Test
  def traitReference(): Unit = doTypeHierarchyCommandCompletionTest(
    s"""trait MyTrait
       |
       |class Child extends ${start}MyTrait$end..$CARET""".stripMargin
  )

  @Test
  def classReferenceAsReturnType(): Unit = doTypeHierarchyCommandCompletionTest(
    s"""class MyClass(val x: Int)
       |
       |object Test {
       |  def create(): ${start}MyClass$end..$CARET = new MyClass(1)
       |}""".stripMargin
  )

  @Test
  def objectReference(): Unit = doTypeHierarchyCommandCompletionTest(
    s"""object MyObject {
       |  val value = 1
       |}
       |
       |object Test {
       |  val x = ${start}MyObject$end..$CARET.value
       |}""".stripMargin
  )

  @Test
  def auxiliaryConstructorDefinition(): Unit = doTypeHierarchyCommandCompletionTest(
    s"""class MyClass(x: Int) {
       |  def ${start}this$end..$CARET() = this(0)
       |}""".stripMargin
  )

  @Test
  def selfInvocation(): Unit = doTypeHierarchyCommandCompletionTest(
    s"""class MyClass(x: Int) {
       |  def this() = ${start}this$end..$CARET(0)
       |}""".stripMargin
  )

  @Test
  def noTypeHierarchyForMethodName(): Unit = checkNoTypeHierarchyCommandCompletion(
    s"""object Test {
       |  def myMethod..$CARET(x: Int): Int = x + 1
       |}""".stripMargin
  )

  @Test
  def noTypeHierarchyForValDeclaration(): Unit = checkNoTypeHierarchyCommandCompletion(
    s"""class MyClass {
       |  val myField..$CARET = 42
       |}""".stripMargin
  )

  @Test
  def noTypeHierarchyForLocalVariable(): Unit = checkNoTypeHierarchyCommandCompletion(
    s"""object Test {
       |  def foo(): Unit = {
       |    val x = 42
       |    x.$CARET
       |  }
       |}""".stripMargin
  )

  @Test
  def noTypeHierarchyForLiteral(): Unit = checkNoTypeHierarchyCommandCompletion(
    s"""object Test {
       |  val x = 42.$CARET
       |}""".stripMargin
  )
}
