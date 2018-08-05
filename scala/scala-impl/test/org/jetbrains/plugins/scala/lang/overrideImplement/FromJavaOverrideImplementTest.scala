package org.jetbrains.plugins.scala
package lang.overrideImplement

import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.{JavaCodeInsightFixtureTestCase, ModuleFixture}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings
import org.junit.Assert.assertEquals

/**
 * Nikolay.Tropin
 * 12/18/13
 */
class FromJavaOverrideImplementTest extends JavaCodeInsightFixtureTestCase {
  protected override def tuneFixture(moduleBuilder: JavaModuleFixtureBuilder[_ <: ModuleFixture]) {
    moduleBuilder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15)
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk14Path.getPath)
  }
  
  def runTest(methodName: String, javaText: String, scalaText: String, expectedText: String, isImplement: Boolean,
              defaultSettings: ScalaCodeStyleSettings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject))) {
    myFixture.addFileToProject("JavaDummy.java", javaText.stripMargin.trim)
    val oldSettings = ScalaCodeStyleSettings.getInstance(getProject).clone()
    val scalaFile = myFixture.configureByText("ScalaDummy.scala", scalaText.replace("\r", "").stripMargin.trim)
    TypeAnnotationSettings.set(getProject, defaultSettings)
    
    ScalaOIUtil.invokeOverrideImplement(myFixture.getProject, myFixture.getEditor, scalaFile, isImplement, methodName)
    TypeAnnotationSettings.set(getProject, oldSettings.asInstanceOf[ScalaCodeStyleSettings])
    assertEquals(expectedText.replace("\r", "").stripMargin.trim, scalaFile.getText.stripMargin.trim)
  }

  def testDefaultImplementations(): Unit = {
    val javaText =
      """
        |public interface JavaDummy {
        |    default int foo() {
        |      return 1;
        |    }
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
        |  override def foo(): Int = super.foo()
        |}
      """
    runTest("foo", javaText, scalaText, expectedText, isImplement = false)
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
    runTest("vararg", javaText, scalaText, expectedText, isImplement = true)
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
    runTest("vararg", javaText, scalaText, expectedText, isImplement = false)
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
    
    val settings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject))
    runTest("def", javaText, scalaText, expectedText, isImplement = false, defaultSettings = TypeAnnotationSettings.noTypeAnnotationForPublic(settings))
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
    runTest("method", javaText, scalaText, expected, isImplement = false)
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
    runTest("method", javaText, scalaText, expected, isImplement = false)
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
    runTest("method", javaText, scalaText, expected, isImplement = false)
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
    runTest("method", javaText, scalaText, expected, isImplement = false)
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
    runTest("method", javaText, scalaText, expectedText, isImplement = false)
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
        |class Child extends JavaDummy[Int] {
        |  override def method[S <: JavaDummy[Int] with JavaDummy.DummyInterface[Int]](arg: Int): Int = super.method(arg)
        |}
      """
    runTest("method", javaText, scalaText, expectedText, isImplement = false)
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
    runTest("getValue", javaText, scalaText, expectedText, isImplement = false)

  }

  def testMap() {
    val javaText = {
      """
        |public interface Map<K,V>
        |    void putAll(Map<? extends K, ? extends V> m);
        |}
      """
    }
    val scalaText =
      """
        |class ExtendsMap[K, V] extends Map[K, V] {
        |  <caret>
        |}
      """
    val expectedText =
      """
        |class ExtendsMap[K, V] extends Map[K, V] {
        |  def putAll(m: Map[_ <: K, _ <: V]): Unit = ???
        |}
      """
    val methodName: String = "putAll"
    val isImplement = true
    runTest(methodName, javaText, scalaText, expectedText, isImplement)
  }
  
  def testSCL14206(): Unit = {
    val java = "public interface Solution { long find(int a[], int n); }"
    
    val scala =
      """
        |class Impl extends Solution {
        |  <caret>
        |}
      """.stripMargin
    
    val expected =
      """
        |class Impl extends Solution {
        |  def find(a: Array[Int], n: Int): Long = ???
        |}
      """.stripMargin
    
    runTest("find", java, scala, expected, isImplement = true)
  }

}
