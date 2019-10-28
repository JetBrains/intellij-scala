package org.jetbrains.plugins.scala
package codeInsight
package intention
package declarations

import java.util.regex.Pattern

import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.ui.{ChooserInterceptor, UiInterceptors}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.util.assertions.ExceptionAssertions

import scala.collection.JavaConverters._

abstract class ChangeAccessModifierIntentionTestBase extends intentions.ScalaIntentionTestBase with ExceptionAssertions {

  def modifierCodeToName(modifier: String): String =
    if (modifier == "") "public" else modifier

  def doTestWithInterceptor(text: String, resultText: String, current: String, toSelect: String, expectedIntentionText: Option[String] = None): Unit = {
    val expectedModifiers = Seq("public", "protected", "private").filter(_ != modifierCodeToName(current))
    UiInterceptors.register(new ChooserInterceptor(expectedModifiers.asJava, Pattern.quote(modifierCodeToName(toSelect))))
    doTest(text, resultText, expectedIntentionText)
    UiInterceptors.clear()
  }

  def doChangeDeclarationModifier(context: String, declType: String, originalModifier: String, newModifier: String): Unit = {
    val originalSpace = if (originalModifier.isEmpty) "" else " "
    val newSpace = if (newModifier.isEmpty) "" else " "
    val original =
      s"""
         |$context ExampleClass {
         |  $originalModifier$originalSpace$declType$CARET example = Unit
         |}
         |""".stripMargin
    val result =
      s"""
         |$context ExampleClass {
         |  $newModifier$newSpace$declType example = Unit
         |}
         |""".stripMargin

    val multipleAvailableModifier = context != "object" || originalModifier == "protected"
    if (multipleAvailableModifier) {
      doTestWithInterceptor(
        original,
        result,
        originalModifier,
        newModifier,
        Some("Change access modifier")
      )
    } else {
      doTest(original, result, Some(s"Make 'example' ${modifierCodeToName(newModifier)}"))
    }
  }
}

class ChangeAccessModifierIntentionTest extends ChangeAccessModifierIntentionTestBase {
  override def familyName = ChangeAccessModifierIntention.familyName

  def normalIntentionText: String = "Change access modifier"
  def specificIntentionText(name: String, newModifier: String): String = s"Make '$name' $newModifier"

  def test_make_top_level_private(): Unit = {
    doTest(s"c${CARET}lass Example", "private class Example", Some(specificIntentionText("Example", "private")))
  }

  def test_make_top_level_public(): Unit = {
    doTest(s"private$CARET class Example", "class Example", Some(specificIntentionText("Example", "public")))
  }


  def test_change_modifier(): Unit = {
    val modifier = Seq("", "protected", "private")
    for {
      context <- Seq("object")
      declType <- Seq("def", "val", "lazy val", "var" , "type")
      from <- modifier
      to <- modifier
      if from != to
      if !(context == "object") || !(to == "protected")
    } {
      doChangeDeclarationModifier(context, declType, from, to)
    }
  }

  def test_local(): Unit = {
    checkIntentionIsNotAvailable(
      s"""
        |class Test {
        |  locally {
        |    def test$CARET(): Unit = ()
        |  }
        |}
        |""".stripMargin
    )
  }

  def test_multi_definition(): Unit = {
    doTest(
      s"""
        |object Test {
        |  val ${CARET}a, b = 3
        |}
        |""".stripMargin,
      """
        |object Test {
        |  private val a, b = 3
        |}
        |""".stripMargin,
      Some("Make 'a, b' private")
    )
  }

  def test_pattern(): Unit = {
    doTest(
      s"""
         |object Test {
         |  val Some($CARET(a, b, _)) = 3
         |}
         |""".stripMargin,
      """
        |object Test {
        |  private val Some((a, b, _)) = 3
        |}
        |""".stripMargin,
      Some("Make 'a, b' private")
    )
  }

  def test_anonymous_pattern(): Unit = {
    doTest(
      s"""
         |object Test {
         |  val Some(${CARET}_) = 3
         |}
         |""".stripMargin,
      """
        |object Test {
        |  private val Some(_) = 3
        |}
        |""".stripMargin,
      Some("Make 'Some(_)' private")
    )
  }

  def test_conflict_function(): Unit = {
    assertExceptionMessage[BaseRefactoringProcessor.ConflictsInTestsException](
      "method <b><code>func()</code></b> with private visibility is not accessible from class <b><code>Usage</code></b>"
    ) {
      doTest(
        s"""
          |object Usage {
          |  Test.func()
          |}
          |
          |object Test {
          |  def$CARET func(): Int = 5
          |}
          |""".stripMargin,
        "",
        Some("Make 'func' private")
      )
    }
  }

  def test_conflict_multi_property(): Unit = {
    assertExceptionMessage[BaseRefactoringProcessor.ConflictsInTestsException](
      """
        |variable <b><code>a</code></b> with private visibility is not accessible from class <b><code>Usage</code></b>
        |variable <b><code>b</code></b> with private visibility is not accessible from class <b><code>Usage</code></b>
        |""".stripMargin.trim.withNormalizedSeparator
    ) {
      doTest(
        s"""
           |object Usage {
           |  Test.a
           |  Test.b
           |}
           |
           |object Test {
           |  val$CARET (a, b) = (1, 2)
           |}
           |""".stripMargin,
        "",
        Some("Make 'a, b' private")
      )
    }
  }

  def test_available_on_access_modifier(): Unit = {
    checkIntentionIsAvailable(
      s"""
        |class Test {
        |  private$CARET def test() = ()
        |}
        |""".stripMargin
    )
  }

  def test_available_on_type_params(): Unit = {
    checkIntentionIsAvailable(
      s"""
         |class Test[X[_$CARET]]
         |""".stripMargin
    )
  }

  def test_available_on_constructor(): Unit = {
    checkIntentionIsAvailable(
      s"""
         |class Test(val x$CARET: Int)
         |""".stripMargin
    )
  }

  def test_available_on_params(): Unit = {
    checkIntentionIsAvailable(
      s"""
         |class Test {
         |  def test(x$CARET: Int): Unit = ()
         |}
         |""".stripMargin
    )
  }

  def test_available_on_return_type(): Unit = {
    checkIntentionIsAvailable(
      s"""
         |class Test {
         |  def test(x: Int): Uni${CARET}t = ()
         |}
         |""".stripMargin
    )
  }

  def test_available_on_type_alias(): Unit = {
    checkIntentionIsAvailable(
      s"""
         |class Test {
         |  type$CARET X = Int
         |}
         |""".stripMargin
    )
  }

  def test_not_available_in_funcdef(): Unit = {
    checkIntentionIsNotAvailable(
      s"""
         |class Test {
         |  private def test() = ($CARET)
         |}
         |""".stripMargin
    )
  }

  def test_not_available_in_funcdef_2(): Unit = {
    checkIntentionIsNotAvailable(
      s"""
         |class Test {
         |  private def test() = $CARET ()
         |}
         |""".stripMargin
    )
  }

  def test_not_available_in_class_body(): Unit = {
    checkIntentionIsNotAvailable(
      s"""
         |class Test {
         |  $CARET
         |}
         |""".stripMargin
    )
  }

  def test_not_available_in_type_alias_def(): Unit = {
    checkIntentionIsNotAvailable(
      s"""
         |class Test {
         |  type X =$CARET Int
         |}
         |""".stripMargin
    )
  }

  def test_not_available_in_comment(): Unit = {
    checkIntentionIsNotAvailable(
      s"""
        |/** @see [[B$CARET]] */
        |class A {
        |}
        |
        |class B {
        |}
        |""".stripMargin
    )
  }
}
