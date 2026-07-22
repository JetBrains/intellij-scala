package org.jetbrains.plugins.scala.lang.overrideImplement

import com.intellij.codeInsight.navigation.{ImplementationSearcher, MethodImplementationsSearch}
import com.intellij.idea.TestFor
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AllOverridingMethodsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.IndexingTestUtil
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{PsiMemberExt, StringExt}
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.junit.Assert
import org.junit.Assert.assertTrue

import java.util
import scala.jdk.CollectionConverters._

class ImplementationSearchTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  def findFromJava(javaText: String, scalaText: String, shouldFoundInClasses: Set[String]): Unit = {
    // Create test files
    myFixture.addFileToProject("DummyScala.scala", scalaText.withNormalizedSeparator.trim)
    myFixture.configureByText("DummyJava.java", javaText.withNormalizedSeparator.trim)

    doFind(shouldFoundInClasses)
  }

  def findFromScala(scalaText: String, javaText: String, shouldFoundInClasses: Set[String]): Unit = {
    // Create test files
    myFixture.addFileToProject("DummyJava.java", javaText.withNormalizedSeparator.trim)
    myFixture.configureByText("DummyScala.scala", scalaText.withNormalizedSeparator.trim)

    doFind(shouldFoundInClasses)
  }

  private def doFind(shouldFoundInClasses: Set[String]): Unit = {
    // Find method at the caret marker
    val atCaret = myFixture.getElementAtCaret
    val method = PsiTreeUtil.getParentOfType(atCaret, classOf[PsiMethod], false)

    //for go to implementations
    val list = new util.ArrayList[PsiMethod]()
    MethodImplementationsSearch.getOverridingMethods(method, list, GlobalSearchScope.allScope(getProject))
    val classNames = list.asScala.map(_.containingClass.getName)
    Assert.assertEquals(s"Wrong set of overriders for $method", shouldFoundInClasses, classNames.toSet)

    //for line markers
    val clazz = method.containingClass
    val overriders2 = AllOverridingMethodsSearch.search(clazz).findAll().asScala
    val classNames2 = overriders2.map(_.second.containingClass.getName)
    Assert.assertEquals(s"Wrong set of overriders for $clazz", shouldFoundInClasses, classNames2.toSet)
  }

  def testRawTypeFromJava(): Unit = {
    val javaText =
      s"""public class JavaClass {
         |    public static class List<T> {}
         |
         |    public void ${CARET}foo(List list) {}
         |}
         |""".stripMargin

    val scalaText =
      """class ScalaClass extends JavaClass {
        |  override def foo(list: JavaClass.List[_]): Unit = ()
        |}
        |
        |class ScalaClass2 extends JavaClass {
        |  override def foo(list: JavaClass.List[_ <: AnyRef]): Unit = ()
        |}
        |""".stripMargin

    findFromJava(javaText, scalaText, Set("ScalaClass"))
  }

  def testRawTypeWithBound(): Unit = {
    val javaText =
      s"""public class JavaClass {
         |    public static interface Bound {}
         |    public static class Bounded<T extends Bound> {}
         |
         |    public void ${CARET}foo(Bounded b) {}
         |}
         |""".stripMargin

    val scalaText =
      """class ScalaClass extends JavaClass {
        |  override def foo(b: JavaClass.Bounded[_ <: JavaClass.Bound]): Unit = ()
        |}
        |
        |class ScalaClass2 extends JavaClass {
        |  override def foo(b: JavaClass.Bounded[_]): Unit = ()
        |}
        |""".stripMargin

    findFromJava(javaText, scalaText, Set("ScalaClass"))
  }

  @TestFor(issues = Array("SCL-25260"))
  def testSearchImplementationsFromJavaBaseWithScalaTraitAndScalaClass(): Unit = {
    findFromJava(
      s"""public class JavaBase {
         |    public int ${CARET}foo() {
         |        return 0;
         |    }
         |}
         |""".stripMargin,
      """trait ScalaTrait extends JavaBase {
        |  override def foo = 1
        |}
        |
        |class ScalaClass extends ScalaTrait {
        |  override def foo = 2
        |}
        |""".stripMargin,
      Set("ScalaTrait", "ScalaClass")
    )
  }

  @TestFor(issues = Array("SCL-25260"))
  def testSearchImplementationsFromScalaTraitWithScalaClass(): Unit = {
    findFromScala(
      s"""trait ScalaTrait extends JavaBase {
         |  override def ${CARET}foo = 1
         |}
         |
         |class ScalaClass extends ScalaTrait {
         |  override def foo = 2
         |}
         |""".stripMargin,
      """public class JavaBase {
        |    public int foo() {
        |        return 0;
        |    }
        |}
        |""".stripMargin,
      Set("ScalaClass")
    )
  }

  private def assertHasImplementationsInClasses(
    containingClassName: String,
    methodName: String,
    expectedClassNamesWithImplementations: Seq[String]
  ): Unit = {
    val actualImplementations = findImplementationsOfMethod(containingClassName, methodName)
    val actualClassNamesWithImplementations = actualImplementations.map(_.containingClass.getName)
    assertCollectionEquals(
      s"Class names containing implementations of method $methodName from class $containingClassName",
      expectedClassNamesWithImplementations.sorted,
      actualClassNamesWithImplementations.sorted
    )
  }

  private def findImplementationsOfMethod(className: String, methodName: String): Seq[PsiMethod] = {
    val clazz = getFixture.findClass(className)

    val methods = clazz.findMethodsByName(methodName, false)
    assertTrue(s"Cannot find method `$methodName` in class `$className`", methods.nonEmpty)
    val method = methods.head

    val implementationSearcher = new ImplementationSearcher()
    val implementations = implementationSearcher.searchImplementations(method, getEditor, false, false)
    implementations.toSeq.map(_.asInstanceOf[PsiMethod])
  }

  private def addCommonJavaScalaDefinitions_SCL19720(): Unit = {
    getFixture.configureByText("JavaDefinitions.java",
      //language=Java
      """interface JavaInterface {
        |    void fooFromJavaInterface1();
        |    void fooFromJavaInterface2();
        |    default void fooFromJavaWithDefaultNonOverridden() { }
        |    default void fooFromJavaWithDefault1() { }
        |    default void fooFromJavaWithDefault2() { }
        |}
        |
        |abstract class JavaAbstractClass {
        |    abstract void fooFromJavaAbstractClass1();
        |    abstract void fooFromJavaAbstractClass2();
        |}
        |
        |abstract class JavaClass1 extends JavaAbstractClass implements JavaInterface {
        |    @Override public void fooFromJavaAbstractClass1() {}
        |    @Override public void fooFromJavaInterface1() {}
        |}
        |abstract class JavaClass2 implements JavaInterface {
        |    @Override public void fooFromJavaInterface1() {}
        |}
        |""".stripMargin
    )

    getFixture.addFileToProject("ScalaDefinitions.scala",
      //language=Scala
      """abstract class ScalaClass1 extends JavaAbstractClass with JavaInterface {
        |  override def fooFromJavaAbstractClass1()(): Unit = {}
        |  override def fooFromJavaInterface1()(): Unit = {}
        |}
        |
        |abstract class ScalaClass2 extends JavaInterface {
        |  override def fooFromJavaInterface1()(): Unit = {}
        |}
        |
        |trait ScalaInterface {
        |  def fooFromScala1(): Unit
        |  def fooFromScala2(): Unit
        |}
        |
        |abstract class ScalaClassWithMixed1 extends ScalaTrait
        |abstract class ScalaClassWithMixed2 extends ScalaAbstractClass1
        |abstract class ScalaClassWithMixed3 extends ScalaAbstractClass2
        |
        |trait ScalaTrait extends AnyRef with JavaInterface with ScalaInterface {
        |  override def fooFromJavaInterface1(): Unit = {}
        |
        |  // no implementation - won't be "mixed" as methods with implementation (won't be physically copied)
        |  override def fooFromJavaInterface2(): Unit
        |
        |  override def fooFromScala1(): Unit = {}
        |}
        |
        |abstract class ScalaAbstractClass1 extends JavaAbstractClass with JavaInterface with ScalaInterface {
        |  override def fooFromJavaInterface1(): Unit = {}
        |  override def fooFromJavaInterface2(): Unit
        |
        |  override def fooFromJavaAbstractClass1()(): Unit = {}
        |  override def fooFromJavaAbstractClass2()(): Unit
        |
        |  override def fooFromJavaWithDefault1()(): Unit = {}
        |
        |  override def fooFromScala1(): Unit = {}
        |}
        |
        |abstract class ScalaAbstractClass2 extends AnyRef with JavaInterface with ScalaInterface {
        |  override def fooFromJavaInterface1(): Unit = {}
        |  override def fooFromJavaInterface2(): Unit
        |  override def fooFromJavaWithDefault2()(): Unit = {}
        |
        |  override def fooFromScala1(): Unit = {}
        |  override def fooFromScala2(): Unit
        |}
        |""".stripMargin)

    IndexingTestUtil.waitUntilIndexesAreReady(getProject)
  }

  //SCL-19720
  def testSearchImplementationsOfJavaBaseMethods_1(): Unit = {
    addCommonJavaScalaDefinitions_SCL19720()
    assertHasImplementationsInClasses("JavaInterface", "fooFromJavaInterface1", Seq(
      "JavaClass1",
      "JavaClass2",
      "ScalaAbstractClass1",
      "ScalaAbstractClass2",
      "ScalaClass1",
      "ScalaClass2",
      "ScalaTrait",
    ))
  }

  def testSearchImplementationsOfJavaBaseMethods_2(): Unit = {
    addCommonJavaScalaDefinitions_SCL19720()
    assertHasImplementationsInClasses("JavaInterface", "fooFromJavaInterface2", Seq(
      "ScalaAbstractClass1",
      "ScalaAbstractClass2",
      "ScalaTrait",
    ))
  }

  def testSearchImplementationsOfJavaBaseMethods_3(): Unit = {
    addCommonJavaScalaDefinitions_SCL19720()
    assertHasImplementationsInClasses("JavaInterface", "fooFromJavaWithDefaultNonOverridden", Seq(
      //no implementations
    ))
  }

  def testSearchImplementationsOfJavaBaseMethods_4(): Unit = {
    addCommonJavaScalaDefinitions_SCL19720()
    assertHasImplementationsInClasses("JavaInterface", "fooFromJavaWithDefault1", Seq(
      "ScalaAbstractClass1",
    ))
  }

  def testSearchImplementationsOfJavaBaseMethods_5(): Unit = {
    addCommonJavaScalaDefinitions_SCL19720()
    assertHasImplementationsInClasses("JavaInterface", "fooFromJavaWithDefault2", Seq(
      "ScalaAbstractClass2",
    ))
  }

  def testSearchImplementationsOfScalaBaseMethods_1(): Unit = {
    addCommonJavaScalaDefinitions_SCL19720()
    assertHasImplementationsInClasses("ScalaInterface", "fooFromScala1", Seq(
      "ScalaTrait",
      "ScalaAbstractClass1",
      "ScalaAbstractClass2",
    ))
  }

  def testSearchImplementationsOfScalaBaseMethods_2(): Unit = {
    addCommonJavaScalaDefinitions_SCL19720()
    assertHasImplementationsInClasses("ScalaInterface", "fooFromScala2", Seq(
      "ScalaAbstractClass2",
    ))
  }

  private def addCommonScalaDefinitions_SCL19720_2(): Unit = {
    getFixture.configureByText("ScalaDefinitions.scala",
      //language=Scala
      """trait ScalaTrait1 {
        |  def fooFromScalaTrait1_1(): Unit
        |  def fooFromScalaTrait1_2(): Unit
        |}
        |
        |trait ScalaTrait2 extends ScalaTrait1 {
        |  override def fooFromScalaTrait1_1(): Unit = {}
        |  def fooFromScalaTrait2_1(): Unit
        |  def fooFromScalaTrait2_2(): Unit = {}
        |}
        |
        |abstract class ScalaAbstractClass1 extends ScalaTrait1 {
        |  override def fooFromScalaTrait1_1(): Unit = {}
        |
        |  def fooFromScalaAbstractClass1_1(): Unit
        |  def fooFromScalaAbstractClass1_2(): Unit = {}
        |}
        |
        |abstract class ScalaAbstractClass2 extends ScalaTrait2 {
        |  override def fooFromScalaTrait1_1(): Unit = {}
        |
        |  def fooFromScalaAbstractClass2_1(): Unit
        |  def fooFromScalaAbstractClass2_2(): Unit = {}
        |}
        |
        |abstract class ScalaClass1 extends ScalaTrait1
        |abstract class ScalaClass2 extends ScalaTrait2
        |abstract class ScalaClass3 extends ScalaAbstractClass1
        |abstract class ScalaClass4 extends ScalaAbstractClass2
        |""".stripMargin
    )

    IndexingTestUtil.waitUntilIndexesAreReady(getProject)
  }

  def testSearchImplementationsOfScalaBaseMethods_2_1(): Unit = {
    addCommonScalaDefinitions_SCL19720_2()
    assertHasImplementationsInClasses("ScalaTrait1", "fooFromScalaTrait1_1", Seq(
      "ScalaAbstractClass1",
      "ScalaAbstractClass2",
      "ScalaTrait2",
    ))
  }

  def testSearchImplementationsOfScalaBaseMethods_2_2(): Unit = {
    addCommonScalaDefinitions_SCL19720_2()
    assertHasImplementationsInClasses("ScalaTrait1", "fooFromScalaTrait1_2", Seq())
  }

  def testSearchImplementationsOfScalaBaseMethods_2_3(): Unit = {
    addCommonScalaDefinitions_SCL19720_2()
    assertHasImplementationsInClasses("ScalaTrait2", "fooFromScalaTrait1_1", Seq(
      "ScalaAbstractClass2"
    ))
  }

  def testSearchImplementationsOfScalaBaseMethods_2_4(): Unit = {
    addCommonScalaDefinitions_SCL19720_2()
    assertHasImplementationsInClasses("ScalaTrait2", "fooFromScalaTrait2_1", Seq())
    assertHasImplementationsInClasses("ScalaTrait2", "fooFromScalaTrait2_2", Seq())
  }

  def testSearchImplementationsOfScalaBaseMethods_2_5(): Unit = {
    addCommonScalaDefinitions_SCL19720_2()
    assertHasImplementationsInClasses("ScalaAbstractClass1", "fooFromScalaTrait1_1", Seq())
    assertHasImplementationsInClasses("ScalaAbstractClass1", "fooFromScalaAbstractClass1_1", Seq())
    assertHasImplementationsInClasses("ScalaAbstractClass1", "fooFromScalaAbstractClass1_2", Seq())

    assertHasImplementationsInClasses("ScalaAbstractClass2", "fooFromScalaTrait1_1", Seq())
    assertHasImplementationsInClasses("ScalaAbstractClass2", "fooFromScalaAbstractClass2_1", Seq())
    assertHasImplementationsInClasses("ScalaAbstractClass2", "fooFromScalaAbstractClass2_2", Seq())
  }

  private def addCommonJavaScalaDefinitions_SCL19720_WithExports(): Unit = {
    getFixture.addFileToProject("JavaDefinitions.java",
      //language=Java
      """public interface JavaInterface {
        |    int fooJava();
        |    int barJava();
        |}
        |""".stripMargin
    )

    getFixture.addFileToProject("ScalaDefinitions.scala",
      //language=Scala
      """trait ScalaTrait {
        |  def fooScala: Int
        |  def barScala: Int
        |}
        |
        |class ScalaClass extends AnyRef with ScalaTrait with JavaInterface {
        |  //These statements add methods that effectively override the methods from ScalaTrait and JavaInterface
        |  export O.fooScala
        |  export O.fooJava
        |  fooScala
        |  fooJava
        |
        |  override def barScala: Int = ???
        |  override def barJava(): Int = ???
        |}
        |
        |// Another class exists to ensure that exported members from ScalaClass (that are detected as implementations)
        |// are not considered as another implementation inside ScalaClass2
        |class ScalaClass2 extends ScalaClass
        |
        |object O {
        |  def fooScala: Int = 0
        |  def fooJava: Int = ???
        |}
        |""".stripMargin)

    IndexingTestUtil.waitUntilIndexesAreReady(getProject)
  }

  def testSearchImplementationsOfJavaBaseMethods_ExportStatementOverrides(): Unit = {
    addCommonJavaScalaDefinitions_SCL19720_WithExports()
    assertHasImplementationsInClasses("JavaInterface", "fooJava", Seq("ScalaClass"))
    assertHasImplementationsInClasses("JavaInterface", "barJava", Seq("ScalaClass"))
  }

  def testSearchImplementationsOfScalaBaseMethods_ExportStatementOverrides(): Unit = {
    addCommonJavaScalaDefinitions_SCL19720_WithExports()
    assertHasImplementationsInClasses("ScalaTrait", "fooScala", Seq("ScalaClass"))
    assertHasImplementationsInClasses("ScalaTrait", "barScala", Seq("ScalaClass"))
  }

  @TestFor(issues = Array("SCL-24850"))
  def testSearchImplementationsOfScala3ExtensionMethodInJavaAndScala(): Unit = {
    val scalaText =
      s"""package foo
         |
         |class Foo {
         |  extension (d: Double) def fo${CARET}o(a: Int, b: String): Unit = ???
         |}
         |
         |class Bar extends Foo {
         |  extension (d: Double) override def foo(a: Int, b: String): Unit = ???
         |}
         |""".stripMargin

    val javaText =
      """package foo;
        |
        |public class JUsage extends foo.Foo {
        |    @Override
        |    public void foo(double d, int a, String b) {
        |        super.foo(d, a, b);
        |    }
        |}
        |""".stripMargin

    findFromScala(scalaText, javaText, Set("Bar", "JUsage"))
  }
}
