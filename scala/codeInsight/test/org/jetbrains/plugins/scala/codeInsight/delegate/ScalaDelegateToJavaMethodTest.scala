package org.jetbrains.plugins.scala.codeInsight.delegate

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.junit.Assert

class ScalaDelegateToJavaMethodTest extends ScalaDelegateMethodTestBase {

  import ScalaDelegateMethodTestBase._

  private def doTestWithJava(
    javaText: String,
    scalaText: String,
    expectedText: String,
    settings: ScalaCodeStyleSettings = defaultSettings(getProject)
  ): Unit = {
    import StringUtil.convertLineSeparators

    implicit val project: Project = getProject
    myFixture.addFileToProject("JavaClass.java", convertLineSeparators(javaText))
    val scalaFile = myFixture.configureByText("ScalaDummy.scala", convertLineSeparators(scalaText))

    implicit val editor: Editor = myFixture.getEditor
    invokeScalaGenerateDelegateHandler(scalaFile, settings)
    Assert.assertEquals(convertLineSeparators(expectedText), convertLineSeparators(scalaFile.getText))
  }

  def testJavaFieldTarget(): Unit = {
    val javaText =
      s"""public class JavaClass {
         |    public static class D {
         |        public int foo(int i) {
         |            return i;
         |        }
         |    }
         |
         |    public D d = new D();
         |}""".stripMargin
    val scalaText =
      s"""class A extends JavaClass {
         |  $CARET
         |}""".stripMargin
    val result =
      s"""class A extends JavaClass {
         |  def foo(i: Int): Int = d.foo(i)
         |}""".stripMargin
    doTestWithJava(javaText, scalaText, result)
  }

  def testJavaGetterTarget(): Unit = {
    val javaText =
      s"""public class JavaClass {
         |    public static class D {
         |        public int foo(int i) {
         |            return i;
         |        }
         |    }
         |
         |    public D getD() {
         |       return new D();
         |    }
         |}""".stripMargin
    val scalaText =
      s"""class A extends JavaClass {
         |  $CARET
         |}""".stripMargin
    val result =
      s"""class A extends JavaClass {
         |  def foo(i: Int): Int = getD.foo(i)
         |}""".stripMargin
    doTestWithJava(javaText, scalaText, result)
  }

  def testPrivateFieldTarget(): Unit = {
    val javaText =
      s"""public class JavaClass {
         |    public static class D {
         |        public int foo(int i) {
         |            return i;
         |        }
         |    }
         |
         |    private D d = new D();
         |}""".stripMargin
    val scalaText =
      s"""class A extends JavaClass {
         |$CARET
         |}""".stripMargin
    val result = //no action
      s"""class A extends JavaClass {
         |
        |}""".stripMargin
    doTestWithJava(javaText, scalaText, result)
  }

  def testDelegateToJavaMethod_NoCaretSet(): Unit = {
    val javaText =
      s"""public class JavaClass {
         |    public int foo(int i) {
         |        return i;
         |    }
         |}""".stripMargin
    val scalaText =
      s"""class A {
         |  val d = new JavaClass()
         |
         |}""".stripMargin
    val result =
      s"""class A {
         |  val d = new JavaClass()
         |
         |  def foo(i: Int): Int = d.foo(i)
         |}""".stripMargin
    doTestWithJava(javaText, scalaText, result)
  }

  def testDelegateToGenericTarget(): Unit = {
    val javaText =
      s"""public class JavaClass<T> {
         |    public T foo(T t) {
         |        return t;
         |    }
         |}""".stripMargin
    val scalaText =
      s"""class A {
         |  val d = new JavaClass[Int]()
         |  $CARET
         |}""".stripMargin
    val result =
      s"""class A {
         |  val d = new JavaClass[Int]()
         |
         |  def foo(t: Int): Int = d.foo(t)
         |}""".stripMargin
    doTestWithJava(javaText, scalaText, result)
  }

  def testDelegateToJavaMethodWithTypeParameter(): Unit = {
    val javaText =
      s"""public class JavaClass {
         |    public <T> T foo() {
         |        return null;
         |    }
         |}""".stripMargin
    val scalaText =
      s"""class A {
         |  val d = new JavaClass()
         |$CARET
         |}""".stripMargin
    val result =
      s"""class A {
         |  val d = new JavaClass()
         |
         |  def foo[T](): T = d.foo()
         |}""".stripMargin
    doTestWithJava(javaText, scalaText, result)
  }

  def testDelegateToJavaMethodWithTypeParameter2(): Unit = {
    val javaText =
      s"""public class JavaClass {
         |    public <T> T foo() {
         |        return null;
         |    }
         |}""".stripMargin
    val scalaText =
      s"""class A {
         |  val d = new JavaClass()
         |$CARET
         |}""".stripMargin
    val result =
      s"""class A {
         |  val d = new JavaClass()
         |
         |  def foo[T]() = d.foo[T]()
         |}""".stripMargin
    doTestWithJava(javaText, scalaText, result, settings = noTypeAnnotationForPublic(getProject))
  }
}
