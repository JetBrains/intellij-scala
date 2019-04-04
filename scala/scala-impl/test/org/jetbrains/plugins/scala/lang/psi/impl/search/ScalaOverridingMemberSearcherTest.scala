package org.jetbrains.plugins.scala.lang.psi.impl.search

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

class ScalaOverridingMemberSearcherTest extends ScalaLightCodeInsightFixtureTestAdapter {
  type NamedElementPath = List[String]

  def getNamedElement(elem: PsiElement, path: NamedElementPath): ScNamedElement = {
    def getInner(elem: PsiElement, path: List[String]): Option[ScNamedElement] = {
      path match {
        case name :: rest =>
          for {
            candidate <- elem.depthFirst().collect { case e: ScNamedElement if e.name == name => e }
            found <- getInner(candidate, rest)
          } {
            return Some(found)
          }
          None
        case _ =>
          Some(elem).collect { case e: ScNamedElement => e }
      }
    }

    getInner(elem, path).getOrElse(throw new NoSuchElementException(s"Element ${path.mkString(".")} was not found"))
  }

  def path(path: String*): List[String] = path.toList

  def check(code: String, origin: NamedElementPath, overriding: Seq[NamedElementPath]): Unit = {
    val file = myFixture.configureByText(ScalaFileType.INSTANCE, code)


    val originElem = getNamedElement(file, origin)
    val expectedOverridingMembers = overriding.map(getNamedElement(file, _))
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

  def test_trait_override(): Unit =check(
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
