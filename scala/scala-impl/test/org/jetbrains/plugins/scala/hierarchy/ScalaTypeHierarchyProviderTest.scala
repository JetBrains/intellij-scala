package org.jetbrains.plugins.scala.hierarchy

import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.PsiSelectionUtil
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers

class ScalaTypeHierarchyProviderTest extends ScalaLightCodeInsightFixtureTestCase with PsiSelectionUtil with AssertionMatchers {
  private def doTest(code: String, targetPath: NamedElementPath): Unit = {
    val file = myFixture.configureByText(ScalaFileType.INSTANCE, code)
    val expectedTarget = selectElement[PsiClass](file, targetPath)

    val provider = new ScalaTypeHierarchyProvider

    val target = provider.getTarget(EditorUtil.getEditorDataContext(myFixture.getEditor))

    expectedTarget shouldBe target
  }

  def test_ref_to_class(): Unit = doTest(
    s"""
       |class Base
       |
       |class Impl extends ${CARET}Base
       |""".stripMargin,
    path("Base")
  )

  def test_ref_to_primary_constructor(): Unit = doTest(
    s"""
       |trait Trait
       |class Base(i: Int)
       |
       |class Impl extends Bas${CARET}e(1) with Trait
       |""".stripMargin,
    path("Base")
  )

  def test_ref_to_secondary_constructor(): Unit = doTest(
    s"""
       |trait Trait
       |class Base(i: Int) {
       |  def this(s: String) = this(1)
       |}
       |
       |class Impl extends Bas${CARET}e("") with Trait
       |""".stripMargin,
    path("Base")
  )

  def test_ref_to_trait(): Unit = doTest(
    s"""
       |trait Trait
       |
       |class Impl extends ${CARET}Trait
       |""".stripMargin,
    path("Trait")
  )

  def test_ref_to_type_alias(): Unit = doTest(
    s"""
       |class Base
       |
       |type Alias = Base
       |
       |class Impl extends ${CARET}Alias
       |""".stripMargin,
    path("Base")
  )

  def test_type_annotation(): Unit = doTest(
    s"""
       |
       |class Blub
       |class Outer {
       |  def test: Bl${CARET}ub = 3
       |}
       |""".stripMargin,
    path("Blub")
  )

  def test_value_def(): Unit = doTest(
    s"""
       |class Blub
       |class Outer {
       |  def te${CARET}st: Blub = 3
       |}
       |""".stripMargin,
    path("Outer") // test has no type hierarchy, so take the outer one
  )

  def test_type_alias(): Unit = doTest(
    s"""
       |class Blub
       |
       |class Outer {
       |  type Al${CARET}ias = Blub
       |}
       |""".stripMargin,
    path("Blub")
  )

  def test_type_alias2(): Unit = doTest(
    s"""
       |class Blub
       |
       |class Outer {
       |  type Alias = Bl${CARET}ub
       |}
       |""".stripMargin,
    path("Blub")
  )

  def test_object(): Unit = doTest(
    s"""
       |class Outer {
       |  object Bl${CARET}ub
       |}
       |""".stripMargin,
    path("Outer", "Blub")
  )
}
