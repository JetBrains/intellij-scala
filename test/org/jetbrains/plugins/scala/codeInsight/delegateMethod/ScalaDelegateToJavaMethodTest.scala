package org.jetbrains.plugins.scala
package codeInsight.delegateMethod

import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.{JavaCodeInsightFixtureTestCase, ModuleFixture}
import org.jetbrains.plugins.scala.codeInsight.delegate.ScalaGenerateDelegateHandler
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings
import org.junit.Assert._

/**
 * Nikolay.Tropin
 * 2014-03-27
 */
class ScalaDelegateToJavaMethodTest  extends JavaCodeInsightFixtureTestCase {
  protected override def tuneFixture(moduleBuilder: JavaModuleFixtureBuilder[_ <: ModuleFixture]) {
    moduleBuilder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15)
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk14Path.getPath)
  }

  def runTest(javaText: String, scalaText: String, expectedText: String,
              codeStyleSettings: ScalaCodeStyleSettings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject))) {
    def clean(s: String): String = s.replace("\r", "").stripMargin.trim

    myFixture.addFileToProject("JavaClass.java", clean(javaText))
    val scalaFile = myFixture.configureByText("ScalaDummy.scala", clean(scalaText))
    val oldSettings = ScalaCodeStyleSettings.getInstance(getProject).clone().asInstanceOf[ScalaCodeStyleSettings]
    TypeAnnotationSettings.set(getProject, codeStyleSettings)
    new ScalaGenerateDelegateHandler().invoke(myFixture.getProject, myFixture.getEditor, scalaFile)
    TypeAnnotationSettings.set(getProject, oldSettings)
    assertEquals(clean(expectedText), clean(scalaFile.getText))
  }

  def testJavaFieldTarget() {
    val javaText =
      """public class JavaClass {
        |    public static class D {
        |        public int foo(int i) {
        |            return i;
        |        }
        |    }
        |
        |    public D d = new D();
        |}"""
    val scalaText =
      """class A extends JavaClass {
        |  <caret>
        |}"""
    val result =
      """class A extends JavaClass {
        |  def foo(i: Int): Int = d.foo(i)
        |}"""
    runTest(javaText, scalaText, result)
  }

  def testJavaGetterTarget() {
    val javaText =
      """public class JavaClass {
        |    public static class D {
        |        public int foo(int i) {
        |            return i;
        |        }
        |    }
        |
        |    public D getD() {
        |       return new D();
        |    }
        |}"""
    val scalaText =
      """class A extends JavaClass {
        |  <caret>
        |}"""
    val result =
      """class A extends JavaClass {
        |  def foo(i: Int): Int = getD.foo(i)
        |}"""
    runTest(javaText, scalaText, result)
  }

  def testPrivateFieldTarget() {
    val javaText =
      """public class JavaClass {
        |    public static class D {
        |        public int foo(int i) {
        |            return i;
        |        }
        |    }
        |
        |    private D d = new D();
        |}"""
    val scalaText =
      """class A extends JavaClass {
        |<caret>
        |}"""
    val result = //no action
      """class A extends JavaClass {
        |
        |}"""
    runTest(javaText, scalaText, result)
  }

  def testDelegateToJavaMethod() {
    val javaText =
      """public class JavaClass {
        |    public int foo(int i) {
        |        return i;
        |    }
        |}"""
    val scalaText =
      """class A {
        |  val d = new JavaClass()
        |
        |}"""
    val result =
      """class A {
        |  val d = new JavaClass()
        |
        |  def foo(i: Int): Int = d.foo(i)
        |}"""
    runTest(javaText, scalaText, result)
  }

  def testDelegateToGenericTarget() {
    val javaText =
      """public class JavaClass<T> {
        |    public T foo(T t) {
        |        return t;
        |    }
        |}"""
    val scalaText =
      """class A {
        |  val d = new JavaClass[Int]()
        |  <caret>
        |}"""
    val result =
      """class A {
        |  val d = new JavaClass[Int]()
        |
        |  def foo(t: Int): Int = d.foo(t)
        |}"""
    runTest(javaText, scalaText, result)
  }

  def testDelegateToJavaMethodWithTypeParameter() {
    val javaText =
      """public class JavaClass {
        |    public <T> T foo() {
        |        return null;
        |    }
        |}"""
    val scalaText =
      """class A {
        |  val d = new JavaClass()
        |<caret>
        |}
        |"""
    val result =
      """class A {
        |  val d = new JavaClass()
        |
        |  def foo[T](): T = d.foo()
        |}"""
    runTest(javaText, scalaText, result)
  }

  def testDelegateToJavaMethodWithTypeParameter2() {
    val javaText =
      """public class JavaClass {
        |    public <T> T foo() {
        |        return null;
        |    }
        |}"""
    val scalaText =
      """class A {
        |  val d = new JavaClass()
        |<caret>
        |}
        |"""
    val result =
      """class A {
        |  val d = new JavaClass()
        |
        |  def foo[T]() = d.foo[T]()
        |}"""


    val settings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject))
    
    runTest(javaText, scalaText, result, TypeAnnotationSettings.noTypeAnnotationForPublic(settings))
  }

  def template() {
    val javaText =
      """"""
    val scalaText =
      """"""
    val result =
      """"""
    runTest(javaText, scalaText, result)
  }
}
