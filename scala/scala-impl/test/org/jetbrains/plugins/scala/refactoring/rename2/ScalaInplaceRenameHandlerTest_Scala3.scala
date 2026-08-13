package org.jetbrains.plugins.scala.refactoring.rename2

import com.intellij.refactoring.rename.PsiElementRenameHandler
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.StringExt
import org.junit.Assert

class ScalaInplaceRenameHandlerTest_Scala3 extends ScalaInplaceRenameHandlerTest {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  /**
   * Elements with a synthetic given name cannot be renamed in place,
   * because there is no name in the code that could be edited.
   */
  private def checkIsNoInplaceHandler(fileText: String): Unit = {
    myFixture.configureByText("dummy.scala", fileText.withNormalizedSeparator.trim)
    checkIsNotAvailable(localHandler)
    checkIsNotAvailable(memberHandler)
  }

  private def checkIsNotRenameableAtAll(fileText: String): Unit = {
    checkIsNoInplaceHandler(fileText)
    Assert.assertTrue(
      "The element is expected to be not renameable",
      PsiElementRenameHandler.isVetoed(myFixture.getElementAtCaret)
    )
  }

  def testAnonymousGiven(): Unit = checkIsNoInplaceHandler(
    s"""trait Foo
       |
       |given Foo = ???
       |
       |val usage = given_F${CARET}oo
       |""".stripMargin
  )

  def testNamedGiven(): Unit = checkIsMemberHandler(
    s"""trait Foo
       |
       |given my${CARET}Given: Foo = ???
       |""".stripMargin
  )

  def testGivenPattern(): Unit = checkIsNotRenameableAtAll(
    s"""trait Foo[A]
       |
       |val usage = ??? match {
       |  case given Foo[a] => given_Fo${CARET}o_a
       |}
       |""".stripMargin
  )
}
