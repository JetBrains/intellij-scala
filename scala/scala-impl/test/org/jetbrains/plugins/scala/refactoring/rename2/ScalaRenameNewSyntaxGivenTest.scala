package org.jetbrains.plugins.scala.refactoring.rename2

import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Assert.assertTrue

/**
 * Same as [[ScalaRenameGivenTest]], but for givens in the syntax introduced in Scala 3.6,
 * see [[org.jetbrains.plugins.scala.lang.parser.parsing.top.template.NewGivenDef]]
 */
class ScalaRenameNewSyntaxGivenTest extends ScalaRenameTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_6

  def testScalaVersionSupportsTheNewGivenSyntax(): Unit =
    assertTrue(s"Unexpected Scala version $version", version.languageLevel >= ScalaLanguageLevel.Scala_3_6)

  //////////////////// renaming the type of an anonymous given ////////////////////

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
    s"""trait Fo${CARET}o:
       |  def foo: Int
       |
       |given Foo:
       |  def foo: Int = 0
       |
       |class usage {
       |  given_Foo
       |}
       |""".stripMargin,
    """trait Bar:
      |  def foo: Int
      |
      |given Bar:
      |  def foo: Int = 0
      |
      |class usage {
      |  given_Bar
      |}
      |""".stripMargin
  )

  def testRenameTypeOfAnonymousConditionalGiven(): Unit = doRenameTest("MySeq",
    s"""trait S${CARET}eq[T]
       |
       |given [T] => Seq[T] = ???
       |
       |class usage {
       |  given_Seq_T
       |}
       |""".stripMargin,
    """trait MySeq[T]
      |
      |given [T] => MySeq[T] = ???
      |
      |class usage {
      |  given_MySeq_T
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

  def testRenameAnonymousStructuralGiven(): Unit = doRenameTest("myFoo",
    s"""trait Foo:
       |  def foo: Int
       |
       |given Foo:
       |  def foo: Int = 0
       |
       |class usage {
       |  given_F${CARET}oo
       |}
       |""".stripMargin,
    """trait Foo:
      |  def foo: Int
      |
      |given myFoo: Foo:
      |  def foo: Int = 0
      |
      |class usage {
      |  myFoo
      |}
      |""".stripMargin
  )

  def testRenameAnonymousConditionalGiven(): Unit = doRenameTest("myGiven",
    s"""given [T] => Seq[T] = ???
       |
       |class usage {
       |  given_Se${CARET}q_T
       |}
       |""".stripMargin,
    """given myGiven: [T] => Seq[T] = ???
      |
      |class usage {
      |  myGiven
      |}
      |""".stripMargin
  )
}
