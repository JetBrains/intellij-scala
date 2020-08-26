package org.jetbrains.plugins.scala.lang.overrideImplement

import java.util

import com.intellij.codeInsight.navigation.MethodImplementationsSearch
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AllOverridingMethodsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.normalize
import org.jetbrains.plugins.scala.extensions.PsiMemberExt
import org.junit.Assert

import scala.jdk.CollectionConverters._

/**
  * Nikolay.Tropin
  * 24-May-17
  */
class ImplementationSearchTest extends JavaCodeInsightFixtureTestCase {

  def findFromJava(javaText: String, scalaText: String, shouldFoundInClasses: Set[String]): Unit = {
    myFixture.addFileToProject("DummyScala.scala", normalize(scalaText))
    myFixture.configureByText("DummyJava.java", normalize(javaText))

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
      """
        |public class JavaClass {
        |    public static class List<T> {}
        |
        |    public void <caret>foo(List list) {}
        |}
        |""".stripMargin

    val scalaText =
      """
        |class ScalaClass extends JavaClass {
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
      """
        |public class JavaClass {
        |    public static interface Bound {}
        |    public static class Bounded<T extends Bound> {}
        |
        |    public void <caret>foo(Bounded b) {}
        |}
      """.stripMargin

    val scalaText =
      """
        |class ScalaClass extends JavaClass {
        |  override def foo(b: JavaClass.Bounded[_ <: JavaClass.Bound]): Unit = ()
        |}
        |
        |class ScalaClass2 extends JavaClass {
        |  override def foo(b: JavaClass.Bounded[_]): Unit = ()
        |}
      """.stripMargin

    findFromJava(javaText, scalaText, Set("ScalaClass"))
  }
}
