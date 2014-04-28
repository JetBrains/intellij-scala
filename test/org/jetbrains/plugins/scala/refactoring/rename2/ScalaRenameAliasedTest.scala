package org.jetbrains.plugins.scala
package refactoring.rename2

import org.junit.Assert

/**
 * User: Jason Zaugg
 *
 * Tests functionality in [[org.jetbrains.plugins.scala.findUsages.ScalaAliasedImportedElementSearcher]]
 * and [[org.jetbrains.plugins.scala.lang.refactoring.rename.ScalaRenameUtil.filterAliasedReferences]]
 */

class ScalaRenameAliasedTest extends ScalaRenameTestBase {
  def testRenameValAliased() {
    val fileText =
      """
      |object test {
      |  object A { val oldValueName<caret> = 1}
      |  A.oldValueName
      |  import A.{oldValueName => aliasedValueName}
      |  aliasedValueName
      |}
      |""".stripMargin('|').replaceAll("\r", "").trim()
    myFixture.configureByText("dummy.scala", fileText)
    val valElement = myFixture.getElementAtCaret
    val usages = myFixture.findUsages(valElement)
    Assert.assertEquals(usages.size(), 3)
    myFixture.renameElementAtCaret("newValueName")

    val resultText =
      """
      |object test {
      |  object A { val newValueName = 1}
      |  A.newValueName
      |  import A.{newValueName => aliasedValueName}
      |  aliasedValueName
      |}
      |""".stripMargin('|').replaceAll("\r", "").trim()

    myFixture.checkResult(resultText)
  }

  def testRenameDefAliased() {
    val fileText =
      """
      |object test {
      |  object A { def oldDefName<caret> = 1}
      |  A.oldDefName
      |  import A.{oldDefName => aliasedDefName}
      |  aliasedDefName
      |}
      |""".stripMargin('|').replaceAll("\r", "").trim()
    myFixture.configureByText("dummy.scala", fileText)
    val defElement = myFixture.getElementAtCaret
    val usages = myFixture.findUsages(defElement)
    Assert.assertEquals(usages.size(), 3)
    myFixture.renameElementAtCaret("newDefName")

    val resultText =
      """
      |object test {
      |  object A { def newDefName = 1}
      |  A.newDefName
      |  import A.{newDefName => aliasedDefName}
      |  aliasedDefName
      |}
      |""".stripMargin('|').replaceAll("\r", "").trim()

    myFixture.checkResult(resultText)
  }

  def testRenameObjectAliased() {
    val fileText =
      """
      |object test {
      |  object A { object oldObjectName<caret>}
      |  A.oldObjectName
      |  import A.{oldObjectName => aliasedObjectName}
      |  aliasedObjectName
      |}
      |""".stripMargin('|').replaceAll("\r", "").trim()
    myFixture.configureByText("dummy.scala", fileText)
    val objectElement = myFixture.getElementAtCaret
    val usages = myFixture.findUsages(objectElement)
    Assert.assertEquals(usages.size(), 3)
    myFixture.renameElementAtCaret("newObjectName")

    val resultText =
      """
      |object test {
      |  object A { object newObjectName}
      |  A.newObjectName
      |  import A.{newObjectName => aliasedObjectName}
      |  aliasedObjectName
      |}
      |""".stripMargin('|').replaceAll("\r", "").trim()

    myFixture.checkResult(resultText)
  }

  def testRenameClassAliased() {
    val fileText =
      """
      |object test {
      |  object A { class oldClassName<caret>}
      |  new A.oldClassName: A.oldClassName
      |  import A.{oldClassName => aliasedClassName}
      |  new aliasedClassName: aliasedClassName
      |}
      |""".stripMargin('|').replaceAll("\r", "").trim()
    myFixture.configureByText("dummy.scala", fileText)
    val objectElement = myFixture.getElementAtCaret
    val usages = myFixture.findUsages(objectElement)
    Assert.assertEquals(usages.size(), 5)
    myFixture.renameElementAtCaret("newClassName")

    val resultText =
      """
      |object test {
      |  object A { class newClassName}
      |  new A.newClassName: A.newClassName
      |  import A.{newClassName => aliasedClassName}
      |  new aliasedClassName: aliasedClassName
      |}
      |""".stripMargin('|').replaceAll("\r", "").trim()

    myFixture.checkResult(resultText)
  }

  def testRenameAliasOfTypeAliasToClass() {
    val fileText =
      """
      |object test {
      |  class X
      |  object A { type oldAliasName<caret> = X}
      |  new A.oldAliasName: A.oldAliasName
      |  import A.{oldAliasName => aliasedAliasName}
      |  new aliasedAliasName: aliasedAliasName
      |}
      |""".stripMargin('|').replaceAll("\r", "").trim()
    myFixture.configureByText("dummy.scala", fileText)
    val objectElement = myFixture.getElementAtCaret
    val usages = myFixture.findUsages(objectElement)
    Assert.assertEquals(usages.size(), 5)
    myFixture.renameElementAtCaret("newAliasName")

    val resultText =
      """
      |object test {
      |  class X
      |  object A { type newAliasName<caret> = X}
      |  new A.newAliasName: A.newAliasName
      |  import A.{newAliasName => aliasedAliasName}
      |  new aliasedAliasName: aliasedAliasName
      |}
      |""".stripMargin('|').replaceAll("\r", "").trim()

    myFixture.checkResult(resultText)
  }

  def testRenameTypeAliasToClass() {
    val fileText =
      """
      |object test {
      |  class X
      |  object A { type oldAliasName<caret> = X}
      |  new A.oldAliasName: A.oldAliasName
      |}
      |""".stripMargin('|').replaceAll("\r", "").trim()
    myFixture.configureByText("dummy.scala", fileText)
    val objectElement = myFixture.getElementAtCaret
    val usages = myFixture.findUsages(objectElement)
    Assert.assertEquals(usages.size(), 2)
    myFixture.renameElementAtCaret("newAliasName")

    val resultText =
      """
      |object test {
      |  class X
      |  object A { type newAliasName<caret> = X}
      |  new A.newAliasName: A.newAliasName
      |}
      |""".stripMargin('|').replaceAll("\r", "").trim()

    myFixture.checkResult(resultText)
  }

  def testRenameClassWithSameNameTypeAlias() {
    val fileText =
      """
        |class oldName<caret>
        |object A {
        |  object B { type oldName = _root_.oldName}
        |  new B.oldName: B.oldName
        |  new _root_.oldName: _root_.oldName
        |}""".stripMargin('|').replaceAll("\r", "").trim()
    myFixture.configureByText("dummy.scala", fileText)
    val objectElement = myFixture.getElementAtCaret
    val usages = myFixture.findUsages(objectElement)
    Assert.assertEquals(usages.size(), 5)
    myFixture.renameElementAtCaret("newName")

    val resultText =
      """
        |class newName
        |object A {
        |  object B { type oldName = _root_.newName}
        |  new B.oldName: B.oldName
        |  new _root_.newName: _root_.newName
        |}""".stripMargin('|').replaceAll("\r", "").trim()

    myFixture.checkResult(resultText)
  }
  // TODO packages.
}

