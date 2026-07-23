package org.jetbrains.plugins.scala.hierarchy

import com.intellij.openapi.editor.ex.util.EditorUtil
import junit.framework.TestCase.assertEquals
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.PsiSelectionUtil
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers

//TODO: currently the test only tests the `ScalaTypeHierarchyProvider.getTarget` implementation and nothing else
// Add real tests for Scala class hierarchy
class ScalaTypeHierarchyProviderTest extends ScalaLightCodeInsightFixtureTestCase with PsiSelectionUtil with AssertionMatchers {

  private def assertTypeHierarchyTarget(
    @Language("Scala") code: String,
    expectedTargetName: String
  ): Unit = {
    myFixture.configureByText(ScalaFileType.INSTANCE, code)

    val typeHierarchyProvider = new ScalaTypeHierarchyProvider
    val dataContext = EditorUtil.getEditorDataContext(myFixture.getEditor)
    val actualTarget = typeHierarchyProvider.getTarget(dataContext)

    assertEquals(
      expectedTargetName,
      actualTarget.getName
    )
  }

  def testTargetElement_ReferenceToClass(): Unit = assertTypeHierarchyTarget(
    s"""class Base
       |
       |class Impl extends ${CARET}Base
       |""".stripMargin,
    "Base"
  )

  def testTargetElement_ReferenceToPrimaryConstructor(): Unit = assertTypeHierarchyTarget(
    s"""trait Trait
       |class Base(i: Int)
       |
       |class Impl extends ${CARET}Base(1) with Trait
       |""".stripMargin,
    "Base"
  )

  def testTargetElement_ReferenceToSecondaryConstructor(): Unit = assertTypeHierarchyTarget(
    s"""trait Trait
       |class Base(i: Int) {
       |  def this(s: String) = this(1)
       |}
       |
       |class Impl extends ${CARET}Base("") with Trait
       |""".stripMargin,
    "Base"
  )

  def testTargetElement_ReferenceToTrait(): Unit = assertTypeHierarchyTarget(
    s"""trait Trait
       |
       |class Impl extends ${CARET}Trait
       |""".stripMargin,
    "Trait"
  )

  def testTargetElement_ReferenceToTypeAlias(): Unit = assertTypeHierarchyTarget(
    s"""class Base
       |
       |type Alias = Base
       |
       |class Impl extends ${CARET}Alias
       |""".stripMargin,
    "Base"
  )

  def testTargetElement_MethodReturnType(): Unit = assertTypeHierarchyTarget(
    s"""class MyClass
       |class Outer {
       |  def test: ${CARET}MyClass = 3
       |}
       |""".stripMargin,
    "MyClass"
  )

  def testTargetElement_MethodDefinition(): Unit = assertTypeHierarchyTarget(
    s"""class MyClass
       |class Outer {
       |  def ${CARET}test: MyClass = 3
       |}
       |""".stripMargin,
    // test has no type hierarchy, so take the outer one
    "Outer"
  )

  def testTargetElement_TypeAliasDefinition(): Unit = assertTypeHierarchyTarget(
    s"""class MyClass
       |
       |class Outer {
       |  type ${CARET}Alias = MyClass
       |}
       |""".stripMargin,
    "MyClass"
  )

  def testTargetElement_TypeAliasRightHandSide(): Unit = assertTypeHierarchyTarget(
    s"""class MyClass
       |
       |class Outer {
       |  type Alias = ${CARET}MyClass
       |}
       |""".stripMargin,
    "MyClass"
  )

  def testTargetElement_ObjectDefinition(): Unit = assertTypeHierarchyTarget(
    s"""class Outer {
       |  object ${CARET}Inner
       |}
       |""".stripMargin,
    "Inner$"
  )
}
