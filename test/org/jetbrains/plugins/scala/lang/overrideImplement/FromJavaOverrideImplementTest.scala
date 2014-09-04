package org.jetbrains.plugins.scala
package lang.overrideImplement

import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.{JavaCodeInsightFixtureTestCase, ModuleFixture}
import junit.framework.Assert.assertEquals
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

/**
 * Nikolay.Tropin
 * 12/18/13
 */
class FromJavaOverrideImplementTest extends JavaCodeInsightFixtureTestCase {
  protected override def tuneFixture(moduleBuilder: JavaModuleFixtureBuilder[_ <: ModuleFixture]) {
    moduleBuilder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15)
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk14Path.getPath)
  }

  def runTest(methodName: String, javaText: String, scalaText: String, expectedText: String,
              isImplement: Boolean, needsInferType: Boolean = true) {
    myFixture.addFileToProject("JavaDummy.java", javaText.stripMargin.trim)
    val scalaFile = myFixture.configureByText("ScalaDummy.scala", scalaText.replace("\r", "").stripMargin.trim)
    val oldSpecifyRetType = ScalaApplicationSettings.getInstance.SPECIFY_RETURN_TYPE_EXPLICITLY
    ScalaApplicationSettings.getInstance.SPECIFY_RETURN_TYPE_EXPLICITLY = needsInferType
    ScalaOIUtil.invokeOverrideImplement(myFixture.getProject, myFixture.getEditor, scalaFile, isImplement, methodName)
    assertEquals(expectedText.replace("\r", "").stripMargin.trim, scalaFile.getText.stripMargin.trim)
    ScalaApplicationSettings.getInstance.SPECIFY_RETURN_TYPE_EXPLICITLY = oldSpecifyRetType
  }

  def testVarargImplement() {
    val javaText =
      """
        |public abstract class JavaDummy {
        |    public abstract void vararg(int... args);
        |}
      """
    val scalaText =
      """
        |class Child extends JavaDummy {
        |  <caret>
        |}
      """
    val expectedText =
      """
        |class Child extends JavaDummy {
        |  def vararg(args: Int*): Unit = ???
        |}
      """
    runTest("vararg", javaText, scalaText, expectedText, isImplement = true, needsInferType = true)
  }

  def testVarargOverride() {
    val javaText =
      """
        |public class JavaDummy {
        |    public void vararg(int... args) {}
        |}
      """
    val scalaText =
      """
        |class Child extends JavaDummy {
        |  <caret>
        |}
      """
    val expectedText =
      """
        |class Child extends JavaDummy {
        |  override def vararg(args: Int*): Unit = super.vararg(args: _*)
        |}
      """
    runTest("vararg", javaText, scalaText, expectedText, isImplement = false, needsInferType = true)
  }

  def testKeywordNames() {
    val javaText =
      """
        |public class JavaDummy {
        |    public void def(int val) {}
        |}
      """
    val scalaText =
      """
        |class Child extends JavaDummy {
        |  <caret>
        |}
      """
    val expectedText =
      """
        |class Child extends JavaDummy {
        |  override def `def`(`val`: Int) = super.`def`(`val`)
        |}
      """
    runTest("def", javaText, scalaText, expectedText, isImplement = false, needsInferType = false)
  }

  def testWithOverrideAnnotation() {
    val javaText =
      """
        |public class JavaDummy {
        |    public void method(int number) {}
        |    public static class Inner extends JavaDummy {
        |        @Override
        |        public void method(int number) {
        |            super.method(number);
        |        }
        |    }
        |}
      """
    val scalaText =
      """
        |class Child extends JavaDummy.Inner {
        | <caret>
        |}
      """
    val expected =
      """
        |class Child extends JavaDummy.Inner {
        |  override def method(number: Int): Unit = super.method(number)
        |}
      """
    runTest("method", javaText, scalaText, expected, isImplement = false, needsInferType = true)
  }

  def testWithoutOverrideAnnotation() {
    val javaText =
      """
        |public class JavaDummy {
        |    public void method(int number) {}
        |    public static class Inner extends JavaDummy {
        |        public void method(int number) {
        |            super.method(number);
        |        }
        |    }
        |}
      """
    val scalaText =
      """
        |class Child extends JavaDummy.Inner {
        | <caret>
        |}
      """
    val expected =
      """
        |class Child extends JavaDummy.Inner {
        |  override def method(number: Int): Unit = super.method(number)
        |}
      """
    runTest("method", javaText, scalaText, expected, isImplement = false, needsInferType = true)
  }

  def testSimpleGenerics() {
    val javaText =
      """
        |public class JavaDummy<T> {
        |    public T method(T arg) {
        |        return arg;
        |    }
        |}
      """
    val scalaText =
      """
        |class Child extends JavaDummy[Int] {
        | <caret>
        |}
      """
    val expected =
      """
        |class Child extends JavaDummy[Int] {
        |  override def method(arg: Int): Int = super.method(arg)
        |}
      """
    runTest("method", javaText, scalaText, expected, isImplement = false, needsInferType = true)
  }

  def testSimpleGenerics2() {
    val javaText =
      """
        |public class JavaDummy<T> {
        |    public T method(T arg) {
        |        return arg;
        |    }
        |}
      """
    val scalaText =
      """
        |class Child[S] extends JavaDummy[S] {
        | <caret>
        |}
      """
    val expected =
      """
        |class Child[S] extends JavaDummy[S] {
        |  override def method(arg: S): S = super.method(arg)
        |}
      """
    runTest("method", javaText, scalaText, expected, isImplement = false, needsInferType = true)
  }

  def testGenerics() {
    val javaText =
      """
        |public class JavaDummy<T, S> {
        |    public T method(JavaDummy<? extends T, ? super S> arg) {
        |        return null;
        |    }
        |}
      """
    val scalaText =
      """
        |class Child extends JavaDummy[Int, Boolean] {
        | <caret>
        |}
      """
    val expectedText =
      """
        |class Child extends JavaDummy[Int, Boolean] {
        |  override def method(arg: JavaDummy[_ <: Int, _ >: Boolean]): Int = super.method(arg)
        |}
      """
    runTest("method", javaText, scalaText, expectedText, isImplement = false, needsInferType = true)
  }

  def testTypeParameter() {
    val javaText =
      """
        |public class JavaDummy<T> {
        |    public <S extends JavaDummy<T> & DummyInterface<T>> int method(int arg) {
        |        return 0;
        |    }
        |
        |    public static interface DummyInterface<S> {}
        |}
      """
    val scalaText =
      """
        |class Child extends JavaDummy[Int] {
        |  <caret>
        |}
      """
    val expectedText =
      """
        |import _root_.JavaDummy.DummyInterface
        |
        |class Child extends JavaDummy[Int] {
        |  override def method[S <: JavaDummy[Int] with DummyInterface[Int]](arg: Int): Int = super.method(arg)
        |}
      """
    runTest("method", javaText, scalaText, expectedText, isImplement = false, needsInferType = true)
  }

  def testQueryLikeMethod() {
    val javaText =
      """
        |public class JavaDummy<T> {
        |    public int getValue() {return 0;}
        |}
      """
    val scalaText =
      """
        |class Child extends JavaDummy[Int] {
        |  <caret>
        |}
      """
    val expectedText =
      """
        |class Child extends JavaDummy[Int] {
        |  override def getValue: Int = super.getValue
        |}
      """
    runTest("getValue", javaText, scalaText, expectedText, isImplement = false, needsInferType = true)

  }

}
