package org.jetbrains.plugins.scala
package codeInspection
package methodSignature

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil

class OverrideAbstractMemberInspectionTest extends InspectionSeverityForcingScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[OverrideAbstractMemberInspection]

  override protected def forcedInspectionSeverity: HighlightDisplayLevel =
    HighlightDisplayLevel.WARNING

  override protected val description: String =
    ScalaInspectionBundle.message("method.signature.override.abstract.member")

  val quickfixHint = ScalaInspectionBundle.message("add.override.modifier.quickfix")

  def test_override_trait(): Unit = {
    val baseText =
      """
        |trait Base {
        |  def fun(): Unit
        |}
      """.stripMargin

    checkTextHasError(
      s"""
        |$baseText
        |
        |class Impl extends Base {
        |  def ${START}fun$END(): Unit = ()
        |}
      """.stripMargin
    )

    testQuickFix(
      s"""
        |$baseText
        |
        |class Impl extends Base {
        |  def f${CARET}un(): Unit = ()
        |}
      """.stripMargin,
      s"""
        |$baseText
        |
        |class Impl extends Base {
        |  override def fun(): Unit = ()
        |}
      """.stripMargin,
      quickfixHint
    )
  }

  def test_override_abstract_class(): Unit = {
    checkTextHasError(
      s"""
         |abstract class Base {
         |  def fun(): Unit
         |}
         |
         |class Impl extends Base {
         |  def ${START}fun$END(): Unit = ()
         |}
      """.stripMargin
    )
  }

  def test_override_with_val(): Unit = {
    val baseText =
      """
        |trait Base {
        |  def variable: Int
        |}
      """.stripMargin

    checkTextHasError(
      s"""
         |$baseText
         |
         |class Impl extends Base {
         |  def ${START}variable$END(): Int = 10
         |}
      """.stripMargin
    )

    testQuickFix(
      s"""
         |$baseText
         |
         |class Impl extends Base {
         |  val var${CARET}iable: Int = 10
         |}
      """.stripMargin,
      s"""
         |$baseText
         |
         |class Impl extends Base {
         |  override val variable: Int = 10
         |}
      """.stripMargin,
      quickfixHint
    )
  }

  def test_override_in_case_class(): Unit = {
    val baseText =
      """
        |trait Base {
        |  def variable: Int
        |}
      """.stripMargin

    checkTextHasError(
      s"""
         |$baseText
         |
         |case class Impl(${START}variable$END: Int) extends Base
       """.stripMargin
    )

    testQuickFix(
      s"""
         |$baseText
         |
         |case class Impl(var${CARET}iable: Int) extends Base
       """.stripMargin,
      s"""
         |$baseText
         |
         |case class Impl(override val variable: Int) extends Base
       """.stripMargin,
      quickfixHint
    )


    // test again with 'val' before the parameter
    testQuickFix(
      s"""
         |$baseText
         |
         |case class Impl(val var${CARET}iable: Int) extends Base
       """.stripMargin,
      s"""
         |$baseText
         |
         |case class Impl(override val variable: Int) extends Base
       """.stripMargin,
      quickfixHint
    )
  }

  def test_override_in_param(): Unit = {
    val baseText =
      """
        |trait Base {
        |  def value: Int
        |}
      """.stripMargin

    checkTextHasError(
      s"""
         |$baseText
         |
         |class Impl(val ${START}value$END: Int) extends Base
       """.stripMargin
    )

    testQuickFix(
      s"""
         |$baseText
         |
         |class Impl(val val${CARET}ue: Int) extends Base
       """.stripMargin,
      s"""
         |$baseText
         |
         |class Impl(override val value: Int) extends Base
       """.stripMargin,
      quickfixHint
    )
  }

  def test_with_annotation(): Unit = {
    val baseText =
      """
        |trait Base {
        |  def value: Int
        |}
      """.stripMargin

    testQuickFix(
      s"""
         |$baseText
         |
         |class Impl(@deprecated val val${CARET}ue: Int) extends Base
       """.stripMargin,
      s"""
         |$baseText
         |
         |class Impl(@deprecated override val value: Int) extends Base
       """.stripMargin,
      quickfixHint
    )
  }

  def test_with_annotation_in_case_class(): Unit = {
    val baseText =
      """
        |trait Base {
        |  def value: Int
        |}
      """.stripMargin

    testQuickFix(
      s"""
         |$baseText
         |
         |case class Impl(@deprecated val${CARET}ue: Int) extends Base
       """.stripMargin,
      s"""
         |$baseText
         |
         |case class Impl(@deprecated override val value: Int) extends Base
       """.stripMargin,
      quickfixHint
    )
  }

  def test_multi_override_val(): Unit = {
    val baseText =
      """
        |trait Base {
        |  def value: Int
        |  def value2: Int
        |}
      """.stripMargin

    checkTextHasError(
      s"""
         |$baseText
         |
         |class Impl extends Base {
         |  def ${START}value$END, value2 = 0
         |}
       """.stripMargin,
      allowAdditionalHighlights = true
    )

    testQuickFix(
      s"""
         |$baseText
         |
         |class Impl extends Base {
         |  def ${START}value$END, value2 = 0
         |}
       """.stripMargin,
      s"""
         |$baseText
         |
         |class Impl extends Base {
         |  override def value, value2 = 0
         |}
       """.stripMargin,
      quickfixHint
    )
  }

  def test_multi_override_val_with_only_one_applying(): Unit = {
    val baseText =
      """
        |trait Base {
        |  def value: Int
        |}
      """.stripMargin

    checkTextHasError(
      s"""
         |$baseText
         |
         |class Impl extends Base {
         |  def ${START}value$END, value2 = 0
         |}
       """.stripMargin,
      allowAdditionalHighlights = true
    )

    testQuickFix(
      s"""
         |$baseText
         |
         |class Impl extends Base {
         |  def ${START}value$END, value2 = 0
         |}
       """.stripMargin,
      s"""
         |$baseText
         |
         |class Impl extends Base {
         |  override def value, value2 = 0
         |}
       """.stripMargin,
      quickfixHint
    )
  }

  def test_override_different_type(): Unit = {
    checkTextHasNoErrors(
      """
        |class Base {
        |  def fun(): Unit = ()
        |}
        |
        |class Impl extends Base {
        |  def fun(): Int = 3
        |}
      """.stripMargin
    )
  }

  def test_override_concrete(): Unit = {
    checkTextHasNoErrors(
      """
        |class Base {
        |  def fun(): Unit = ()
        |}
        |
        |class Impl extends Base {
        |  def fun(): Unit = ()
        |}
      """.stripMargin
    )
  }

  def test_not_overriding_param(): Unit = {
    checkTextHasNoErrors(
      """
        |class Base {
        |  def fun: Int
        |}
        |
        |class Impl(fun: Int) extends Base
      """.stripMargin
    )
  }

  def test_var_param(): Unit = {
    checkTextHasNoErrors(
      """
        |class Base {
        |  def fun: Int
        |}
        |
        |class Impl(var fun: Int) extends Base
      """.stripMargin
    )
  }

  def test_overriding_bean_property_in_param(): Unit = {
    checkTextHasNoErrors(
      """
        |class Base {
        |  def getFun: Int
        |}
        |
        |class Impl(@scala.beans.BeanProperty val fun: Int) extends Base
      """.stripMargin
    )
  }

  def test_overriding_bean_property(): Unit = {
    checkTextHasNoErrors(
      """
        |class Base {
        |  def getFun: Int
        |}
        |
        |class Impl extends Base {
        |  @scala.beans.BeanProperty
        |  val fun: Int = 3
        |}
      """.stripMargin
    )
  }
}
