package org.jetbrains.plugins.scala
package lang.overrideImplement

import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings
import org.junit.Assert.assertEquals

class FromJavaOverrideImplementTest extends JavaCodeInsightFixtureTestCase {
  protected override def tuneFixture(moduleBuilder: JavaModuleFixtureBuilder[_]): Unit = {
    moduleBuilder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15)
    // TODO: the path returned from IdeaTestUtil.getMockJdk14Path is invalid in the scala plugin
    //       because the mock-jdk14 does only exists in the intellij-community source
    //       we either have to copy the mock directory into our repo as well or just not add it at all
    //moduleBuilder.addJdk(IdeaTestUtil.getMockJdk14Path.getPath)
  }
  
  def runTest(methodName: String, javaText: String, scalaText: String, expectedText: String, isImplement: Boolean,
              defaultSettings: ScalaCodeStyleSettings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject))): Unit = {
    myFixture.addFileToProject("JavaDummy.java", javaText.stripMargin.trim)
    val oldSettings = ScalaCodeStyleSettings.getInstance(getProject).clone()
    val scalaFile = myFixture.configureByText("ScalaDummy.scala", scalaText.replace("\r", "").stripMargin.trim)
    TypeAnnotationSettings.set(getProject, defaultSettings)

    ScalaOIUtil.invokeOverrideImplement(scalaFile, isImplement, methodName)(getProject, myFixture.getEditor)
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
      """.stripMargin
    val scalaText =
      """
        |class Child extends JavaDummy {
        |  <caret>
        |}
      """.stripMargin
    val expectedText =
      """
        |class Child extends JavaDummy {
        |  override def foo(): Int = super.foo()
        |}
      """.stripMargin
    runTest("foo", javaText, scalaText, expectedText, isImplement = false)
  }

  def testVarargImplement(): Unit = {
    val javaText =
      """
        |public abstract class JavaDummy {
        |    public abstract void vararg(int... args);
        |}
      """.stripMargin
    val scalaText =
      """
        |class Child extends JavaDummy {
        |  <caret>
        |}
      """.stripMargin
    val expectedText =
      """
        |class Child extends JavaDummy {
        |  def vararg(args: Int*): Unit = ???
        |}
      """.stripMargin
    runTest("vararg", javaText, scalaText, expectedText, isImplement = true)
  }

  def testVarargOverride(): Unit = {
    val javaText =
      """
        |public class JavaDummy {
        |    public void vararg(int... args) {}
        |}
      """.stripMargin
    val scalaText =
      """
        |class Child extends JavaDummy {
        |  <caret>
        |}
      """.stripMargin
    val expectedText =
      """
        |class Child extends JavaDummy {
        |  override def vararg(args: Int*): Unit = super.vararg(args: _*)
        |}
      """.stripMargin
    runTest("vararg", javaText, scalaText, expectedText, isImplement = false)
  }

  def testKeywordNames(): Unit = {
    val javaText =
      """
        |public class JavaDummy {
        |    public void def(int val) {}
        |}
      """.stripMargin
    val scalaText =
      """
        |class Child extends JavaDummy {
        |  <caret>
        |}
      """.stripMargin
    val expectedText =
      """
        |class Child extends JavaDummy {
        |  override def `def`(`val`: Int) = super.`def`(`val`)
        |}
      """.stripMargin
    
    val settings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject))
    runTest("def", javaText, scalaText, expectedText, isImplement = false, defaultSettings = TypeAnnotationSettings.noTypeAnnotationForPublic(settings))
  }

  def testWithOverrideAnnotation(): Unit = {
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
      """.stripMargin
    val scalaText =
      """
        |class Child extends JavaDummy.Inner {
        | <caret>
        |}
      """.stripMargin
    val expected =
      """
        |class Child extends JavaDummy.Inner {
        |  override def method(number: Int): Unit = super.method(number)
        |}
      """.stripMargin
    runTest("method", javaText, scalaText, expected, isImplement = false)
  }

  def testWithoutOverrideAnnotation(): Unit = {
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
      """.stripMargin
    val scalaText =
      """
        |class Child extends JavaDummy.Inner {
        | <caret>
        |}
      """.stripMargin
    val expected =
      """
        |class Child extends JavaDummy.Inner {
        |  override def method(number: Int): Unit = super.method(number)
        |}
      """.stripMargin
    runTest("method", javaText, scalaText, expected, isImplement = false)
  }

  def testSimpleGenerics(): Unit = {
    val javaText =
      """
        |public class JavaDummy<T> {
        |    public T method(T arg) {
        |        return arg;
        |    }
        |}
      """.stripMargin
    val scalaText =
      """
        |class Child extends JavaDummy[Int] {
        | <caret>
        |}
      """.stripMargin
    val expected =
      """
        |class Child extends JavaDummy[Int] {
        |  override def method(arg: Int): Int = super.method(arg)
        |}
      """.stripMargin
    runTest("method", javaText, scalaText, expected, isImplement = false)
  }

  def testSimpleGenerics2(): Unit = {
    val javaText =
      """
        |public class JavaDummy<T> {
        |    public T method(T arg) {
        |        return arg;
        |    }
        |}
      """.stripMargin
    val scalaText =
      """
        |class Child[S] extends JavaDummy[S] {
        | <caret>
        |}
      """.stripMargin
    val expected =
      """
        |class Child[S] extends JavaDummy[S] {
        |  override def method(arg: S): S = super.method(arg)
        |}
      """.stripMargin
    runTest("method", javaText, scalaText, expected, isImplement = false)
  }

  def testGenerics(): Unit = {
    val javaText =
      """
        |public class JavaDummy<T, S> {
        |    public T method(JavaDummy<? extends T, ? super S> arg) {
        |        return null;
        |    }
        |}
      """.stripMargin
    val scalaText =
      """
        |class Child extends JavaDummy[Int, Boolean] {
        | <caret>
        |}
      """.stripMargin
    val expectedText =
      """
        |class Child extends JavaDummy[Int, Boolean] {
        |  override def method(arg: JavaDummy[_ <: Int, _ >: Boolean]): Int = super.method(arg)
        |}
      """.stripMargin
    runTest("method", javaText, scalaText, expectedText, isImplement = false)
  }

  def testTypeParameter(): Unit = {
    val javaText =
      """
        |public class JavaDummy<T> {
        |    public <S extends JavaDummy<T> & DummyInterface<T>> int method(int arg) {
        |        return 0;
        |    }
        |
        |    public static interface DummyInterface<S> {}
        |}
      """.stripMargin
    val scalaText =
      """
        |class Child extends JavaDummy[Int] {
        |  <caret>
        |}
      """.stripMargin
    val expectedText =
      """
        |class Child extends JavaDummy[Int] {
        |  override def method[S <: JavaDummy[Int] with JavaDummy.DummyInterface[Int]](arg: Int): Int = super.method(arg)
        |}
      """.stripMargin
    runTest("method", javaText, scalaText, expectedText, isImplement = false)
  }

  def testQueryLikeMethod(): Unit = {
    val javaText =
      """
        |public class JavaDummy<T> {
        |    public int getValue() {return 0;}
        |}
      """.stripMargin
    val scalaText =
      """
        |class Child extends JavaDummy[Int] {
        |  <caret>
        |}
      """.stripMargin
    val expectedText =
      """
        |class Child extends JavaDummy[Int] {
        |  override def getValue: Int = super.getValue
        |}
      """.stripMargin
    runTest("getValue", javaText, scalaText, expectedText, isImplement = false)

  }

  def testMap(): Unit = {
    val javaText = {
      """
        |public interface Map<K,V>
        |    void putAll(Map<? extends K, ? extends V> m);
        |}
      """.stripMargin
    }
    val scalaText =
      """
        |class ExtendsMap[K, V] extends Map[K, V] {
        |  <caret>
        |}
      """.stripMargin
    val expectedText =
      """
        |class ExtendsMap[K, V] extends Map[K, V] {
        |  def putAll(m: Map[_ <: K, _ <: V]): Unit = ???
        |}
      """.stripMargin
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

  def testParameterTypeWithWildcard(): Unit = {
    val java =
      """
        |public interface A<T extends Foo> {
        |    void foo(A<?> a)
        |}
      """.stripMargin

    val scala =
      """
        |class B extends A[Foo] {
        |  <caret>
        |}
        |
        |trait Foo
      """.stripMargin

    val expected =
      """
        |class B extends A[Foo] {
        |  def foo(a: A[_]): Unit = ???
        |}
        |
        |trait Foo
      """.stripMargin

    runTest("foo", java, scala, expected, isImplement = true)
  }

  def testRawParameterType(): Unit = {
    val java =
      """
        |public interface A<T extends Foo> {
        |    void foo(A a)
        |}
      """.stripMargin

    val scala =
      """
        |class B extends A[Foo] {
        |  <caret>
        |}
        |
        |trait Foo
      """.stripMargin

    val expected =
      """
        |class B extends A[Foo] {
        |  def foo(a: A[_ <: Foo]): Unit = ???
        |}
        |
        |trait Foo
      """.stripMargin

    runTest("foo", java, scala, expected, isImplement = true)
  }

}
