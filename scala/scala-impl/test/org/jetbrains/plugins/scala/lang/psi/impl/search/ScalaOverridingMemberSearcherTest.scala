package org.jetbrains.plugins.scala.lang.psi.impl.search

import com.intellij.ide.scratch.ScratchRootType
import com.intellij.psi.{PsiElement, PsiManager}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.util.PsiSelectionUtil
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaLanguage}
import org.junit.Assert.assertEquals

class ScalaOverridingMemberSearcherTest extends ScalaLightCodeInsightFixtureTestCase with PsiSelectionUtil {

  def check(code: String, origin: NamedElementPath, overriding: Seq[NamedElementPath]): Unit = {
    val file = myFixture.configureByText(ScalaFileType.INSTANCE, code)

    val originElem = selectElement[ScNamedElement](file, origin)
    val expectedOverridingMembers = overriding.map(selectElement[ScNamedElement](file, _))
    val foundOverridingMembers = ScalaOverridingMemberSearcher.search(originElem, withSelfType = true)

    def getLineNumber(element: PsiElement): Int =
      file.getText.substring(0, element.getTextRange.getStartOffset).count(_ =='\n')

    val name = origin.last
    expectedOverridingMembers.foreach { expected =>
      if (!foundOverridingMembers.contains(expected)) {
        val lineNumber = getLineNumber(expected)
        throw new AssertionError(s"Function $name in line $lineNumber was not found to override the original function")
      }
    }

    val notFoundMembers = foundOverridingMembers.filter(!expectedOverridingMembers.contains(_))
    notFoundMembers.foreach { notFound =>
      val lineNumber = getLineNumber(notFound)
      throw new AssertionError(s"Function $name in line $lineNumber should not have been found")
    }

    foundOverridingMembers
      .groupBy(identity)
      .values
      .withFilter(_.length > 1)
      .map(_.head)
      .foreach { foundMultipleTimes =>
        val lineNumber = getLineNumber(foundMultipleTimes)
        throw new AssertionError(s"Function $name in line $lineNumber was found multiple times")
      }

    assert(foundOverridingMembers.length == expectedOverridingMembers.length)
  }

  def test_not_overridden(): Unit = check(
    """
      |trait Trait {
      |  def test(): Unit
      |}
      |
    """.stripMargin,
    path("Trait", "test"),
    Seq.empty
  )

  def test_normal_overridden(): Unit = check(
    """
      |trait Trait {
      |  def test(): Unit
      |}
      |
      |class Class extends Trait {
      |  override def test(): Unit = ()
      |}
    """.stripMargin,
    path("Trait", "test"),
    Seq(
      path("Class", "test")
    )
  )

  def test_trait_chain_override(): Unit =check(
    """
      |trait Trait {
      |  def test(): Unit
      |}
      |
      |trait Impl extends Trait {
      |  override def test(): Unit = ()
      |}
      |
      |class Class extends Trait with Impl {
      |  override def test(): Unit = ()
      |}
    """.stripMargin,
    path("Trait", "test"),
    Seq(
      path("Impl", "test"),
      path("Class", "test")
    )
  )

  def test_selftype_override(): Unit = check(
    """
      |trait Trait {
      |  def test(): Unit
      |}
      |
      |trait Impl { this: Trait =>
      |  override def test(): Unit = ()
      |}
    """.stripMargin,
    path("Trait", "test"),
    Seq(
      path("Impl", "test")
    )
  )

  def test_selftype_override_redundant(): Unit = check(
    """
      |trait Trait {
      |  def test(): Unit
      |}
      |
      |trait Impl extends Trait { this: Trait =>
      |  override def test(): Unit = ()
      |}
    """.stripMargin,
    path("Trait", "test"),
    Seq(
      path("Impl", "test")
    )
  )

  def test_indirect_selftype_override(): Unit = check(
    """
      |trait Trait {
      |  def test(): Unit
      |}
      |
      |trait Impl { this: Class =>
      |  override def test(): Unit = ()
      |}
      |
      |class Class extends Trait with Impl
    """.stripMargin,
    path("Trait", "test"),
    Seq(
      path("Impl", "test")
    )
  )

  def test_multiple_parallel_overrides(): Unit = check(
    """
      |trait Base {
      |  def test(): Unit = ()
      |}
      |
      |class Impl extends Base {
      |  override def test(): Unit = ()
      |}
      |
      |class Impl2 extends Base {
      |  override def test(): Unit = ()
      |}
      |
    """.stripMargin,
    path("Base", "test"),
    Seq(
      path("Impl", "test"),
      path("Impl2", "test")
    )
  )

  def test_multiple_protected_parallel_overrides(): Unit = check(
    """
      |trait Base {
      |  protected def test(): Unit = ()
      |}
      |
      |class Impl extends Base {
      |  protected override def test(): Unit = ()
      |}
      |
      |class Impl2 extends Base {
      |  protected override def test(): Unit = ()
      |}
      |
    """.stripMargin,
    path("Base", "test"),
    Seq(
      path("Impl", "test"),
      path("Impl2", "test")
    )
  )

  def test_scratch_file(): Unit = {
    val scratchFileText =
      """trait A { def foo: String };
        |object B extends A { override def foo = "" }""".stripMargin
    val scratchVFile = ScratchRootType.getInstance.createScratchFile(getProject, "foo.sc", ScalaLanguage.INSTANCE, scratchFileText)
    myFixture.configureFromExistingVirtualFile(scratchVFile)
    val scratchPsiFile = PsiManager.getInstance(getProject).findFile(scratchVFile)
    val fooMethodElement = selectElement[ScNamedElement](scratchPsiFile, List("A", "foo"))
    val res = ScalaOverridingMemberSearcher.search(fooMethodElement)
    assertEquals(
      "Wong number of overriding members",
      1,
      res.length
    )
  }

  def test_compound_selftype_override(): Unit = check(
    """
      |trait A {
      |  def method() :String
      |}
      |
      |trait B { }
      |
      |trait M {
      |  self: A with B =>
      |
      |  override def method() = "Hello World"
      |}
    """.stripMargin,
    path("A", "method"),
    Seq(
      path("M", "method")
    )
  )

  // todo: fix this
  /*
  def test_indirect_multiple_selftype_override(): Unit = check(
    """
      |trait Trait {
      |  def test(): Unit
      |}
      |
      |trait Impl { this: Class =>
      |  override def test(): Unit = ()
      |}
      |
      |trait Impl2 { this: Class =>
      |  override def test(): Unit = ()
      |}
      |
      |class Class extends Trait with Impl with Impl2
    """.stripMargin,
    path("Trait", "test"),
    Seq(
      path("Impl", "test"),
      path("Impl2", "test")
    )
  )*/
}
