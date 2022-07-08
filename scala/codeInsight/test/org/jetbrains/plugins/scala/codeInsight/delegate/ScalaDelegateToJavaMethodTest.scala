package org.jetbrains.plugins.scala
package codeInsight
package delegate

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.junit.Assert

class ScalaDelegateToJavaMethodTest extends fixtures.JavaCodeInsightFixtureTestCase
  with ScalaDelegateMethodTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}
  import ScalaDelegateMethodTestBase._
  import builders.JavaModuleFixtureBuilder

  protected override def tuneFixture(moduleBuilder: JavaModuleFixtureBuilder[_]): Unit = {
    moduleBuilder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15)
    // TODO: the path returned from IdeaTestUtil.getMockJdk14Path is invalid in the scala plugin
    //       because the mock-jdk14 does only exists in the intellij-community source
    //       we either have to copy the mock directory into our repo as well or just not add it at all
    //moduleBuilder.addJdk(IdeaTestUtil.getMockJdk14Path.getPath)
  }

  private def doTest(javaText: String, scalaText: String, expectedText: String,
                     settings: ScalaCodeStyleSettings = defaultSettings(getProject)): Unit = {
    import StringUtil.convertLineSeparators

    implicit val project: Project = getProject
    myFixture.addFileToProject("JavaClass.java", convertLineSeparators(javaText))
    val scalaFile = myFixture.configureByText("ScalaDummy.scala", convertLineSeparators(scalaText))

    implicit val editor: Editor = myFixture.getEditor
    doTest(scalaFile, settings)
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
    doTest(javaText, scalaText, result)
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
    doTest(javaText, scalaText, result)
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
    doTest(javaText, scalaText, result)
  }

  def testDelegateToJavaMethod(): Unit = {
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
    doTest(javaText, scalaText, result)
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
    doTest(javaText, scalaText, result)
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
    doTest(javaText, scalaText, result)
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
    doTest(javaText, scalaText, result, settings = noTypeAnnotationForPublic(getProject))
  }

  def template(): Unit =
    doTest("""""", """""", """""")
}
