package org.jetbrains.plugins.scala
package lang.psi.impl.search

import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.util.PsiSelectionUtil
import org.junit.experimental.categories.Category

@Category(Array(classOf[LanguageTests]))
class ScalaOverridingMemberSearcherTest extends ScalaLightCodeInsightFixtureTestAdapter with PsiSelectionUtil {


  def check(code: String, origin: NamedElementPath, overriding: Seq[NamedElementPath]): Unit = {
    val file = myFixture.configureByText(ScalaFileType.INSTANCE, code)


    val originElem = selectElement[ScNamedElement](file, origin)
    val expectedOverridingMembers = overriding.map(selectElement[ScNamedElement](file, _))
    val foundOverridingMembers = ScalaOverridingMemberSearcher.search(originElem, withSelfType = true)

    val name = origin.last
    expectedOverridingMembers.foreach { expected =>
      if (!foundOverridingMembers.contains(expected)) {
        val linenum = file.getText.substring(0, expected.getTextRange.getStartOffset).count(_ =='\n')
        throw new AssertionError(s"Function $name in line $linenum was not found to override the original function")
      }
    }

    val notFoundMembers = foundOverridingMembers.filter(!expectedOverridingMembers.contains(_))
    notFoundMembers.foreach { notFound =>
      val linenum = file.getText.substring(0, notFound.getTextRange.getStartOffset).count(_ =='\n')
      throw new AssertionError(s"Function $name in line $linenum should not have been found")
    }

    foundOverridingMembers
      .groupBy(identity)
      .values
      .withFilter(_.length > 1)
      .map(_.head)
      .foreach { foundMultipleTimes =>
        val linenum = file.getText.substring(0, foundMultipleTimes.getTextRange.getStartOffset).count(_ =='\n')
        throw new AssertionError(s"Function $name in line $linenum was found multiple times")
      }

    assert(foundOverridingMembers.length == expectedOverridingMembers.length)
  }

  def test_not_overriden(): Unit = check(
    """
      |trait Trait {
      |  def test(): Unit
      |}
      |
    """.stripMargin,
    path("Trait", "test"),
    Seq.empty
  )

  def test_normal_overriden(): Unit = check(
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
