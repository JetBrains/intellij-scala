package org.jetbrains.plugins.scala.refactoring.rename2

import org.jetbrains.plugins.scala.extensions.StringExt
import org.junit.Assert

/**
 * Tests functionality in:
 *  - [[org.jetbrains.plugins.scala.findUsages.ScalaAliasedImportedElementSearcher]]
 *  - [[org.jetbrains.plugins.scala.lang.refactoring.rename.ScalaRenameUtil.filterAliasedReferences]]
 */
class ScalaRenameAliasedTest extends ScalaRenameTestBase {

  //noinspection SameParameterValue
  protected def doRenameTestWithUsagesCheck(
    newName: String,
    fileText: String,
    resultText: String,
    expectedUsagesCount: Int
  ): Unit = {
    myFixture.configureByText("dummy2.scala", fileText.withNormalizedSeparator.trim)
    val elementAtCaret = myFixture.getElementAtCaret

    val usages = myFixture.findUsages(elementAtCaret)
    Assert.assertEquals("Usages count is wrong", expectedUsagesCount, usages.size)

    myFixture.renameElementAtCaret(newName)
    myFixture.checkResult(resultText.withNormalizedSeparator.trim)
  }

  def testRenameValAliased(): Unit = {
    doRenameTestWithUsagesCheck(
      "newNameForDefinition",
      s"""
         |object test {
         |
         |  object A {
         |    val oldValueName$CARET = 1
         |  }
         |
         |  A.oldValueName
         |
         |  import A.{oldValueName => aliasedValueName}
         |
         |  aliasedValueName
         |}
         |""".stripMargin,
      """object test {
        |
        |  object A {
        |    val newNameForDefinition = 1
        |  }
        |
        |  A.newNameForDefinition
        |
        |  import A.{newNameForDefinition => aliasedValueName}
        |
        |  aliasedValueName
        |}""".stripMargin,
      3
    )
  }

  def testRenameDefAliased(): Unit = {
    doRenameTestWithUsagesCheck(
      "newNameForDefinition",
      s"""
         |object test {
         |
         |  object A {
         |    def oldDefName$CARET = 1
         |  }
         |
         |  A.oldDefName
         |
         |  import A.{oldDefName => aliasedDefName}
         |
         |  aliasedDefName
         |}
         |""".stripMargin,
      """
        |object test {
        |
        |  object A {
        |    def newNameForDefinition = 1
        |  }
        |
        |  A.newNameForDefinition
        |
        |  import A.{newNameForDefinition => aliasedDefName}
        |
        |  aliasedDefName
        |}""".stripMargin,
      3
    )
  }

  def testRenameObjectAliased(): Unit = {
    doRenameTestWithUsagesCheck(
      "newNameForDefinition",
      s"""
         |object test {
         |
         |  object A {
         |
         |    object oldObjectName$CARET
         |
         |  }
         |
         |  A.oldObjectName
         |
         |  import A.{oldObjectName => aliasedObjectName}
         |
         |  aliasedObjectName
         |}
         |""".stripMargin,
      """
        |object test {
        |
        |  object A {
        |
        |    object newNameForDefinition
        |
        |  }
        |
        |  A.newNameForDefinition
        |
        |  import A.{newNameForDefinition => aliasedObjectName}
        |
        |  aliasedObjectName
        |}""".stripMargin,
      //FIXME: for some reason it shows results for same object 2 times in the "Find usages" toolwindow
      // (this test used to test `.distinct` values before, which was 3)
      6
    )
  }

  def testRenameClassAliased(): Unit = {
    doRenameTestWithUsagesCheck(
      "newNameForDefinition",
      s"""
         |object test {
         |
         |  object A {
         |
         |    class oldClassName$CARET
         |
         |  }
         |
         |  new A.oldClassName: A.oldClassName
         |
         |  import A.{oldClassName => aliasedClassName}
         |
         |  new aliasedClassName: aliasedClassName
         |}
         |""".stripMargin,
      """
        |object test {
        |
        |  object A {
        |
        |    class newNameForDefinition
        |
        |  }
        |
        |  new A.newNameForDefinition: A.newNameForDefinition
        |
        |  import A.{newNameForDefinition => aliasedClassName}
        |
        |  new aliasedClassName: aliasedClassName
        |}""".stripMargin,
      5
    )
  }

  def testRenameAliasOfTypeAliasToClass(): Unit = {
    doRenameTestWithUsagesCheck(
      "newNameForDefinition",
      s"""
         |object test {
         |
         |  class X
         |
         |  object A {
         |    type oldAliasName$CARET = X
         |  }
         |
         |  new A.oldAliasName: A.oldAliasName
         |
         |  import A.{oldAliasName => aliasedAliasName}
         |
         |  new aliasedAliasName: aliasedAliasName
         |}
         |""".stripMargin,
      """
        |object test {
        |
        |  class X
        |
        |  object A {
        |    type newNameForDefinition = X
        |  }
        |
        |  new A.newNameForDefinition: A.newNameForDefinition
        |
        |  import A.{newNameForDefinition => aliasedAliasName}
        |
        |  new aliasedAliasName: aliasedAliasName
        |}""".stripMargin,
      5
    )
  }

  def testRenameTypeAliasToClass(): Unit = {
    doRenameTestWithUsagesCheck(
      "newNameForDefinition",
      s"""
         |object test {
         |
         |  class X
         |
         |  object A {
         |    type oldAliasName$CARET = X
         |  }
         |
         |  new A.oldAliasName: A.oldAliasName
         |}
         |""".stripMargin,
      """
        |object test {
        |
        |  class X
        |
        |  object A {
        |    type newNameForDefinition = X
        |  }
        |
        |  new A.newNameForDefinition: A.newNameForDefinition
        |}""".stripMargin,
      2
    )
  }

  def testRenameClassWithSameNameTypeAlias(): Unit = {
    doRenameTestWithUsagesCheck(
      "newNameForDefinition",
      s"""
         |class oldName$CARET
         |
         |object A {
         |
         |  object B {
         |    type oldName = _root_.oldName
         |  }
         |
         |  new B.oldName: B.oldName
         |  new _root_.oldName: _root_.oldName
         |}""".stripMargin,
      """
        |class newNameForDefinition
        |
        |object A {
        |
        |  object B {
        |    type oldName = _root_.newNameForDefinition
        |  }
        |
        |  new B.oldName: B.oldName
        |  new _root_.newNameForDefinition: _root_.newNameForDefinition
        |}""".stripMargin,
      5
    )
  }

  def testRenameTypeAliasToClass_AtDefinitionPosition(): Unit = doRenameTest(
    "MyAliasRenamed",
    s"""object Wrapper {
       |  class MyClass
       |  type ${CARET}MyAlias = MyClass
       |  new MyAlias
       |  Option.empty[MyAlias]
       |  val x: MyAlias = null
       |}
       |""".stripMargin,
    """object Wrapper {
      |  class MyClass
      |  type MyAliasRenamed = MyClass
      |  new MyAliasRenamed
      |  Option.empty[MyAliasRenamed]
      |  val x: MyAliasRenamed = null
      |}
      |""".stripMargin,
  )

  def testRenameTypeAliasToClass_AtConstructorInvocationPosition(): Unit = doRenameTest(
    "MyAliasRenamed",
    s"""object Wrapper {
       |  class MyClass
       |  type MyAlias = MyClass
       |  new ${CARET}MyAlias
       |  Option.empty[MyAlias]
       |  val x: MyAlias = null
       |}
       |""".stripMargin,
    """object Wrapper {
      |  class MyClass
      |  type MyAliasRenamed = MyClass
      |  new MyAliasRenamed
      |  Option.empty[MyAliasRenamed]
      |  val x: MyAliasRenamed = null
      |}
      |""".stripMargin,
  )

  def testRenameTypeAliasToClass_AtConstructorInvocationPosition_InTheEndOfAliasName(): Unit = doRenameTest(
    "MyAliasRenamed",
    s"""object Wrapper {
       |  class MyClass
       |  type MyAlias = MyClass
       |  new MyAlias$CARET
       |  Option.empty[MyAlias]
       |  val x: MyAlias = null
       |}
       |""".stripMargin,
    """object Wrapper {
      |  class MyClass
      |  type MyAliasRenamed = MyClass
      |  new MyAliasRenamed
      |  Option.empty[MyAliasRenamed]
      |  val x: MyAliasRenamed = null
      |}
      |""".stripMargin,
  )

  def testRenameTypeAliasToClass_AtConstructorInvocationPosition_InTheEndOfAliasName_InTheEndOfFile(): Unit = doRenameTest(
    "MyAliasRenamed",
    s"""class MyClass
       |type MyAlias = MyClass
       |Option.empty[MyAlias]
       |val x: MyAlias = null
       |new MyAlias$CARET""".stripMargin,
    """class MyClass
      |type MyAliasRenamed = MyClass
      |Option.empty[MyAliasRenamed]
      |val x: MyAliasRenamed = null
      |new MyAliasRenamed""".stripMargin,
  )

  def testRenameTypeAliasToClass_AtTypeElementPosition(): Unit = doRenameTest(
    "MyAliasRenamed",
    s"""object Wrapper {
       |  class MyClass
       |  type MyAlias = MyClass
       |  new MyAlias
       |  Option.empty[MyAlias]
       |  val x: ${CARET}MyAlias = null
       |}
       |""".stripMargin,
    """object Wrapper {
      |  class MyClass
      |  type MyAliasRenamed = MyClass
      |  new MyAliasRenamed
      |  Option.empty[MyAliasRenamed]
      |  val x: MyAliasRenamed = null
      |}
      |""".stripMargin,
  )
}
