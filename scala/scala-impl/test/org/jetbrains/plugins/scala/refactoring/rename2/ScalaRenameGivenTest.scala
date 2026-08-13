package org.jetbrains.plugins.scala.refactoring.rename2

import org.jetbrains.plugins.scala.ScalaVersion
import org.junit.Assert.assertEquals

/**
 * Tests renaming of anonymous givens and of the synthetic names they get,
 * see [[org.jetbrains.plugins.scala.lang.refactoring.rename.ScalaGivenRenameUtil]]
 */
class ScalaRenameGivenTest extends ScalaRenameTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  //////////////////// renaming the type of an anonymous given ////////////////////

  def testRenameTypeOfAnonymousGivenAliasDeclaration(): Unit = doRenameTest("Bar",
    s"""abstract class Fo${CARET}o
       |
       |given Foo
       |
       |class usage {
       |  summon[Foo]
       |  given_Foo
       |}
       |""".stripMargin,
    """abstract class Bar
      |
      |given Bar
      |
      |class usage {
      |  summon[Bar]
      |  given_Bar
      |}
      |""".stripMargin
  )

  def testRenameTypeOfAnonymousGivenAliasDefinition(): Unit = doRenameTest("Bar",
    s"""class Fo${CARET}o
       |
       |given Foo = new Foo
       |
       |class usage {
       |  given_Foo
       |}
       |""".stripMargin,
    """class Bar
      |
      |given Bar = new Bar
      |
      |class usage {
      |  given_Bar
      |}
      |""".stripMargin
  )

  def testRenameTypeOfAnonymousStructuralGiven(): Unit = doRenameTest("Bar",
    s"""trait Fo${CARET}o
       |
       |given Foo with {}
       |
       |class usage {
       |  summon[Foo]
       |  given_Foo
       |}
       |""".stripMargin,
    """trait Bar
      |
      |given Bar with {}
      |
      |class usage {
      |  summon[Bar]
      |  given_Bar
      |}
      |""".stripMargin
  )

  def testRenameTypeArgumentOfAnonymousGiven(): Unit = doRenameTest("Bar",
    s"""trait Fo${CARET}o
       |
       |given Seq[Foo] = Seq.empty
       |
       |class usage {
       |  given_Seq_Foo
       |}
       |""".stripMargin,
    """trait Bar
      |
      |given Seq[Bar] = Seq.empty
      |
      |class usage {
      |  given_Seq_Bar
      |}
      |""".stripMargin
  )

  def testRenameTypeAliasOfAnonymousGiven(): Unit = doRenameTest("MyAlias2",
    s"""type MyAli${CARET}as = Int
       |
       |given MyAlias = 0
       |
       |class usage {
       |  given_MyAlias
       |}
       |""".stripMargin,
    """type MyAlias2 = Int
      |
      |given MyAlias2 = 0
      |
      |class usage {
      |  given_MyAlias2
      |}
      |""".stripMargin
  )

  def testRenameObjectInSingletonTypeOfAnonymousGiven(): Unit = doRenameTest("P",
    s"""object ${CARET}O
       |
       |given O.type = O
       |
       |class usage {
       |  given_O_type
       |}
       |""".stripMargin,
    """object P
      |
      |given P.type = P
      |
      |class usage {
      |  given_P_type
      |}
      |""".stripMargin
  )

  def testRenameTypeOfGivenPattern(): Unit = doRenameTest("Bar",
    s"""trait Fo${CARET}o[A]
       |
       |class usage {
       |  ??? match {
       |    case given Foo[a] => given_Foo_a
       |  }
       |}
       |""".stripMargin,
    """trait Bar[A]
      |
      |class usage {
      |  ??? match {
      |    case given Bar[a] => given_Bar_a
      |  }
      |}
      |""".stripMargin
  )

  /** The name of the given doesn't contain the deeply nested type argument, so it must not be renamed */
  def testRenameTypeThatDoesNotContributeToGivenName(): Unit = doRenameTest("Bar",
    s"""trait Fo${CARET}o
       |
       |given Seq[Seq[Foo]] = Seq.empty
       |
       |class usage {
       |  given_Seq_Seq
       |}
       |""".stripMargin,
    """trait Bar
      |
      |given Seq[Seq[Bar]] = Seq.empty
      |
      |class usage {
      |  given_Seq_Seq
      |}
      |""".stripMargin
  )

  def testRenameTypeOfNamedGiven(): Unit = doRenameTest("Bar",
    s"""trait Fo${CARET}o
       |
       |given myGiven: Foo with {}
       |
       |class usage {
       |  myGiven
       |}
       |""".stripMargin,
    """trait Bar
      |
      |given myGiven: Bar with {}
      |
      |class usage {
      |  myGiven
      |}
      |""".stripMargin
  )

  //////////////////// renaming an anonymous given itself ////////////////////

  def testRenameAnonymousGivenAliasDefinition(): Unit = doRenameTest("myFoo",
    s"""abstract class Foo
       |
       |given Foo = ???
       |
       |class usage {
       |  given_F${CARET}oo
       |}
       |""".stripMargin,
    """abstract class Foo
      |
      |given myFoo: Foo = ???
      |
      |class usage {
      |  myFoo
      |}
      |""".stripMargin
  )

  def testRenameAnonymousGivenAliasDeclaration(): Unit = doRenameTest("myFoo",
    s"""abstract class Foo
       |
       |given Foo
       |
       |class usage {
       |  given_F${CARET}oo
       |}
       |""".stripMargin,
    """abstract class Foo
      |
      |given myFoo: Foo
      |
      |class usage {
      |  myFoo
      |}
      |""".stripMargin
  )

  def testRenameAnonymousStructuralGiven(): Unit = doRenameTest("myFoo",
    s"""trait Foo
       |
       |given Foo with {}
       |
       |class usage {
       |  given_F${CARET}oo
       |}
       |""".stripMargin,
    """trait Foo
      |
      |given myFoo: Foo with {}
      |
      |class usage {
      |  myFoo
      |}
      |""".stripMargin
  )

  def testRenameAnonymousGivenWithTypeParameterClause(): Unit = doRenameTest("myGiven",
    s"""given [T]: Seq[T] = Seq.empty
       |
       |class usage {
       |  given_Se${CARET}q_T
       |}
       |""".stripMargin,
    """given myGiven[T]: Seq[T] = Seq.empty
      |
      |class usage {
      |  myGiven
      |}
      |""".stripMargin
  )

  def testRenameAnonymousGivenWithUsingClause(): Unit = doRenameTest("myGiven",
    s"""trait Foo
       |
       |given (using i: Int): Foo = ???
       |
       |class usage {
       |  given_F${CARET}oo
       |}
       |""".stripMargin,
    """trait Foo
      |
      |given myGiven(using i: Int): Foo = ???
      |
      |class usage {
      |  myGiven
      |}
      |""".stripMargin
  )

  def testRenameAnonymousGivenWithEndMarker(): Unit = doRenameTest("myFoo",
    s"""trait Foo
       |
       |given Foo with
       |  def foo: Int = 0
       |end given
       |
       |class usage {
       |  given_F${CARET}oo
       |}
       |""".stripMargin,
    """trait Foo
      |
      |given myFoo: Foo with
      |  def foo: Int = 0
      |end myFoo
      |
      |class usage {
      |  myFoo
      |}
      |""".stripMargin
  )

  def testRenameNamedGiven(): Unit = doRenameTest("myGiven",
    s"""trait Foo
       |
       |given ol${CARET}dName: Foo = ???
       |
       |class usage {
       |  oldName
       |}
       |""".stripMargin,
    """trait Foo
      |
      |given myGiven: Foo = ???
      |
      |class usage {
      |  myGiven
      |}
      |""".stripMargin
  )

  //////////////////// usages in other files ////////////////////

  def testRenameTypeOfAnonymousGivenUsedInAnotherFile(): Unit = {
    val usageFile = myFixture.addFileToProject("usage.scala",
      """class usage {
        |  summon[Foo]
        |  given_Foo
        |}
        |""".stripMargin
    )

    doRenameTest("Bar",
      s"""trait Fo${CARET}o
         |
         |given Foo with {}
         |""".stripMargin,
      """trait Bar
        |
        |given Bar with {}
        |""".stripMargin
    )

    assertEquals(
      """class usage {
        |  summon[Bar]
        |  given_Bar
        |}
        |""".stripMargin,
      usageFile.getText
    )
  }
}
