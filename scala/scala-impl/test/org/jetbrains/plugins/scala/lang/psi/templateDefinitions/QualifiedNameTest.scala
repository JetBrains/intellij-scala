package org.jetbrains.plugins.scala.lang.psi.templateDefinitions

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.junit.Assert

class QualifiedNameTest extends SimpleTestCase {
  private val caretMarker = EditorTestUtil.CARET_TAG

  private def doTest(expectedScalaFQN: String, expectedJavaFQN: String, fileText: String): Unit = {
    val (scalaFile, caret) = parseScalaFileAndGetCaretPosition(fileText, caretMarker)
    val atCaret = scalaFile.findElementAt(caret)
    val scalaClass = PsiTreeUtil.getParentOfType(atCaret, classOf[ScTemplateDefinition])
    Assert.assertEquals("Wrong Java FQN:", expectedJavaFQN, scalaClass.getQualifiedName)
    Assert.assertEquals("Wrong Scala FQN", expectedScalaFQN, scalaClass.qualifiedName)
  }

  private def checkNoFqn(fileText: String): Unit = doTest(null, null, fileText)

  def testTopLevelClass(): Unit = doTest("test.A", "test.A",
    s"""package test
       |
       |class ${caretMarker}A
    """.stripMargin)

  def testTopLevelObject(): Unit = doTest("test.A", "test.A$",
    s"""package test
       |
       |object ${caretMarker}A
    """.stripMargin)

  def testInnerStaticClass(): Unit = doTest("test.A.B", "test.A.B",
    s"""package test
       |
       |object A {
       |  class ${caretMarker}B
       |}
     """.stripMargin)

  def testInnerStaticObject(): Unit = doTest("test.A.B", "test.A.B$",
    s"""package test
       |
       |object A {
       |  object ${caretMarker}B
       |}
     """.stripMargin)

  def testInnerClass(): Unit = doTest("test.A.B", "test.A.B",
    s"""package test
       |
       |class A {
       |  class ${caretMarker}B
       |}
     """.stripMargin)

  def testLocalClass(): Unit = doTest("B", null,
    s"""package test
       |
       |class A {
       |  def foo() {
       |    class ${caretMarker}B
       |    ()
       |  }
       |}
     """.stripMargin)

  def testAnonymousClass(): Unit = checkNoFqn(
    s"""package test
       |
       |class A {
       |  new ${caretMarker}A {
       |    def foo() = null
       |  }
       |}
      """.stripMargin)

  def testInAnonymousClass(): Unit = doTest("Problem", null,
    s"""
       |trait Model
       |
       |class MyApp(myModel: Model) extends App
       |
       |object Main extends MyApp(new Model {
       |  class ${caretMarker}Problem
       |})
      """.stripMargin)

  def testPackageObject(): Unit = doTest("foo.bar", "foo.bar.package$",
    s"""package foo
       |
       |package object ${caretMarker}bar {
       |
       |}
    """.stripMargin)

  //classes defined in package objects are not accessible from java but can be accessible
  def testInPackageObject(): Unit = doTest("foo.bar.A", "foo.bar.package.A",
    s"""package foo
       |
       |package object bar {
       |  class ${caretMarker}A
       |}
    """.stripMargin)

}
