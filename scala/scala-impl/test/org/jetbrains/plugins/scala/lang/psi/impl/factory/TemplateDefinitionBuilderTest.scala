package org.jetbrains.plugins.scala.lang.psi.impl.factory

import junit.framework.TestCase.{assertEquals, assertTrue}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTemplateDefinition, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{TemplateDefKind, TemplateDefinitionBuilder}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

import scala.reflect.{ClassTag, classTag}

abstract class TemplateDefinitionBuilderTestBase extends ScalaLightCodeInsightFixtureTestCase {

  override protected def setUp(): Unit = {
    super.setUp()
    myFixture.configureByText("test.scala", "")
  }

  protected def doTest[T <: ScTemplateDefinition : ClassTag](builder: TemplateDefinitionBuilder,
                                                             expectedText: String): Unit = {
    val definition = builder.createTemplateDefinition()
    val expectedClass = classTag[T].runtimeClass
    val actualClass = definition.getClass
    assertTrue(s"Expected class ${expectedClass.getCanonicalName} is not assignable from created definition's class ${actualClass.getCanonicalName}",
      expectedClass.isAssignableFrom(actualClass))
    assertEquals(expectedText.withNormalizedSeparator, definition.getText.withNormalizedSeparator)
  }

  def testClass(): Unit = doTest[ScClass](
    TemplateDefinitionBuilder(kind = TemplateDefKind.Class).withProjectContext(getProject),
    "class td"
  )

  def testClassName(): Unit = doTest[ScClass](
    TemplateDefinitionBuilder(kind = TemplateDefKind.Class, name = "MyClass").withProjectContext(getProject),
    "class MyClass"
  )

  def testClassWithBlockDefaultFeatures(): Unit = doTest[ScClass](
    TemplateDefinitionBuilder(kind = TemplateDefKind.Class, needsBlock = true).withProjectContext(getProject),
    "class td {}"
  )

  def testTrait(): Unit = doTest[ScTrait](
    TemplateDefinitionBuilder(kind = TemplateDefKind.Trait).withProjectContext(getProject),
    "trait td"
  )

  def testObject(): Unit = doTest[ScObject](
    TemplateDefinitionBuilder(kind = TemplateDefKind.Object).withProjectContext(getProject),
    "object td"
  )
}

class TemplateDefinitionBuilderTest extends TemplateDefinitionBuilderTestBase {
  def testClassWithBlockGivenContext(): Unit = doTest[ScClass](
    TemplateDefinitionBuilder(kind = TemplateDefKind.Class, needsBlock = true, context = getFile),
    "class td {}"
  )

  def testClassBody(): Unit = doTest[ScClass](
    TemplateDefinitionBuilder(kind = TemplateDefKind.Class, body = "val field: Int = 2").withProjectContext(getProject),
    "class td {val field: Int = 2}"
  )

  def testClassBody2(): Unit = doTest[ScClass](
    TemplateDefinitionBuilder(kind = TemplateDefKind.Class, body = "\nval field: Int = 2\n").withProjectContext(getProject),
    """class td {
      |val field: Int = 2
      |}""".stripMargin
  )
}

class TemplateDefinitionBuilderTest_Scala3 extends TemplateDefinitionBuilderTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3

  def testClassWithBlockGivenContext(): Unit = doTest[ScClass](
    TemplateDefinitionBuilder(kind = TemplateDefKind.Class, needsBlock = true, context = getFile),
    """class td:
      |end td""".stripMargin
  )

  def testClassBody(): Unit = doTest[ScClass](
    TemplateDefinitionBuilder(kind = TemplateDefKind.Class, body = "\n  val field: Int = 2", context = getFile),
    """class td:
      |  val field: Int = 2""".stripMargin
  )

  def testClassBody2(): Unit = doTest[ScClass](
    TemplateDefinitionBuilder(kind = TemplateDefKind.Class, body = "\n  val field: Int = 2", context = getFile, needsBlock = true),
    """class td:
      |  val field: Int = 2
      |end td""".stripMargin
  )

  def testMultilineBody(): Unit = doTest[ScClass](
    TemplateDefinitionBuilder(kind = TemplateDefKind.Class, body = "\n  call(3):\n    some_expr\n", context = getFile),
    """class td:
      |  call(3):
      |    some_expr""".stripMargin
  )
}
