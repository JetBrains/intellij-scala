package org.jetbrains.plugins.scala.scalai18n.codeInspection.i18n.internal

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.{ScalaAnnotatorQuickFixTestBase, ScalaInspectionTestBase}

class ReferencePassedToNlsInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ReferencePassedToNlsInspection]

  override protected val description = ScalaI18nBundle.message("internal.expression.without.nls.passed.to.nls")

  protected override def setUp(): Unit = {
    super.setUp()

    myFixture.addFileToProject(
      "org/jetbrains/annotations/nls.java",
      """
        |package org.jetbrains.annotations;
        |
        |public @interface Nls {
        |  enum Capitalization {
        |    NotSpecified,
        |    Title,
        |    Sentence
        |  }
        |  Capitalization capitalization() default Capitalization.NotSpecified;
        |}
        |
        |""".stripMargin
    )
  }

  override protected def createTestText(text: String): String =
    s"""
       |object com {
       |  object intellij {
       |    object openapi {
       |      object util {
       |        class NlsSafe extends scala.annotation.StaticAnnotation
       |      }
       |    }
       |  }
       |}
       |
       |import com.intellij.openapi.util.NlsSafe
       |import org.jetbrains.annotations.Nls
       |
       |@Nls
       |class SpecificNls extends scala.annotation.StaticAnnotation
       |
       |@Nls
       |val nls: String = null
       |val nonnls: String = null
       |@NlsSafe
       |val nlssafe: String = null
       |def toNls(@Nls arg: String): Unit = ()
       |def toNlsSafe(@NlsSafe arg: String): Unit = ()
       |
       |$text
       |""".stripMargin

  def test_simple_val_ref(): Unit =
    checkTextHasError(
      s"""
         |val ref = "blub"
         |toNls(${START}ref$END)
         |""".stripMargin)

  def test_simple_def_ref(): Unit =
    checkTextHasError(
      s"""
         |def ref = "blub"
         |toNls(${START}ref$END)
         |""".stripMargin)

  def test_annotated_ref(): Unit =
    checkTextHasNoErrors(
      s"""
         |@Nls
         |def ref = null
         |toNls(ref)
         |""".stripMargin)

  def test_nls_safe_ref(): Unit =
    checkTextHasNoErrors(
      s"""
         |toNls(nlssafe)
         |""".stripMargin)

  def test_simple_ref_to_nls_safe(): Unit =
    checkTextHasNoErrors(
      s"""
         |toNlsSafe(nonnls)
         |""".stripMargin)

  def test_inner_def(): Unit =
    checkTextHasNoErrors(
      s"""
         |def ref = nls
         |toNls(ref)
         |""".stripMargin)

  def test_inner_val(): Unit =
    checkTextHasNoErrors(
      s"""
         |val ref = nls
         |toNls(ref)
         |""".stripMargin)

  def test_inner_var(): Unit =
    checkTextHasError(
      s"""
         |var ref = nls
         |toNls(${START}ref$END)
         |""".stripMargin)

  def test_recursion_pos(): Unit =
    checkTextHasNoErrors(
      s"""
         |def ref = {
         |  if ("blub" == null) nls
         |  else ref
         |}
         |toNls(ref)
         |""".stripMargin)

  def test_recursion_neg(): Unit =
    checkTextHasError(
      s"""
         |def ref = {
         |  if ("blub" == null) "blub"
         |  else ref
         |}
         |toNls(${START}ref$END)
         |""".stripMargin)

  def test_if(): Unit =
    checkTextHasNoErrors(
      s"""
         |def ref = true
         |toNls(if (ref) nls else nls)
         |""".stripMargin)

  def test_reffed_if(): Unit =
    checkTextHasNoErrors(
      s"""
         |
         |val ref = if ("blub" != null) nls else nls
         |toNls(ref)
         |""".stripMargin)

  def test_reffed_if_neg(): Unit =
    checkTextHasError(
      s"""
         |val ref = if ("blub" != null) nls else "blub"
         |toNls(${START}ref$END)
         |""".stripMargin)

  def test_switch(): Unit =
    checkTextHasNoErrors(
      s"""
         |def ref = true
         |toNls(ref match { case `ref` if ref => nls)
         |""".stripMargin)

  def test_multi_param_clauses(): Unit =
    checkTextHasError(
      s"""
         |def toNls2(@Nls a: String)(@Nls b: String): Unit = ()
         |def ref1 = "blub"
         |def ref2 = "blub2"
         |toNls2(${START}ref1$END)(${START}ref2$END)
         |""".stripMargin
    )

  def test_multi_param_clauses2(): Unit =
    checkTextHasError(
      s"""
         |def toNls2(a: String)(@Nls b: String): Unit = ()
         |def ref1 = "blub"
         |def ref2 = "blub2"
         |toNls2(ref1)(${START}ref2$END)
         |""".stripMargin
    )
  def test_specific_annotation(): Unit =
    checkTextHasError(
      raw"""
           |def toSpecificNls(@SpecificNls arg: String): Unit = ()
           |def ref = "blub2"
           |toSpecificNls(${START}ref$END)
           |""".stripMargin
    )

  def test_unapply_with_nls(): Unit =
    checkTextHasNoErrors(
      s"""
         |case class Test(@Nls text: String)
         |
         |(null: Any) match {
         |  case Test(ref) => toNls(ref)
         |}
         |""".stripMargin
    )

  def test_unapply_without_nls(): Unit =
    checkTextHasError(
      s"""
         |case class Test(text: String)
         |
         |(null: Any) match {
         |  case Test(ref) => toNls(${START}ref$END)
         |}
         |""".stripMargin
    )

  def test_case_class_without_nls(): Unit =
    checkTextHasError(
      s"""
         |case class Test(@Nls text: String)
         |Test(${START}nonnls$END)
         |""".stripMargin
    )

  def test_case_class_with_nls(): Unit =
    checkTextHasNoErrors(
      s"""
         |case class Test(@Nls text: String)
         |Test(${START}nls$END)
         |""".stripMargin
    )

  def test_nls_on_type(): Unit = {
    myFixture.addFileToProject(
      "additional/java/AJavaValue.java",
      """package additional.java;
        |
        |import org.jetbrains.annotations.Nls;
        |
        |public class AJavaValue {
        |  public @Nls String getNlsValue() {
        |    return null;
        |  }
        |}
        |""".stripMargin
    )

    checkTextHasNoErrors(
      """
        |import additional.java.AJavaValue
        |
        |val cls = new AJavaValue
        |toNls(cls.getNlsValue)
        |""".stripMargin
    )
  }

  def test_annotated_java_fields(): Unit = {
    myFixture.addFileToProject(
      "additional/java/label.java",
      """package additional.java;
        |
        |import org.jetbrains.annotations.Nls;
        |
        |public @Nls(capitalization = Nls.Capitalization.Sentence)  @interface Label {
        |
        |}
        |
        |class SomethingWithLabel {
        |  public static final @Label String SOME_LABEL = null;
        |}
        |""".stripMargin
    )

    checkTextHasNoErrors(
      """
        |import additional.java.Label;
        |import additional.java.SomethingWithLabel;
        |
        |toNls(SomethingWithLabel.SOME_LABEL)
        |""".stripMargin
    )
  }

  def test_expr_in_definition(): Unit = checkTextHasError(
    s"""
       |val ref = "blub" + "blub"
       |toNls(${START}ref$END)
       |""".stripMargin
  )

  def test_inheritable_definition(): Unit = checkTextHasError(
    s"""
       |class A {
       |  def ref = nls
       |  def test = toNls(${START}ref$END)
       |}
       |
       |trait B {
       |  def ref: String
       |  def test = toNls(${START}ref$END)
       |}
       |
       |class C {
       |  final def ref = nls
       |  def test = toNls(ref)
       |}
       |
       |final class D {
       |  def ref = nls
       |  def test = toNls(ref)
       |}
       |
       |object E {
       |  def ref = nls
       |  def test = toNls(ref)
       |}
       |""".stripMargin
  )

  def test_special_methods(): Unit = checkTextHasNoErrors(
    """
      |toNls(nls.trim)
      |toNls(nls.strip())
      |""".stripMargin
  )

  def test_special_methods2(): Unit = checkTextHasError(
    s"""
      |toNls(${START}nonnls.trim$END)
      |toNls(${START}nonnls.strip()$END)
      |""".stripMargin
  )

  def test_fix_inner(): Unit = testQuickFix(
    s"""
       |val ref = nonnls
       |toNls(ref.trim)
       |""".stripMargin,
    """
      |@Nls
      |val ref = nonnls
      |toNls(ref.trim)
      |""".stripMargin,
    "Annotate with @Nls"
  )
}
