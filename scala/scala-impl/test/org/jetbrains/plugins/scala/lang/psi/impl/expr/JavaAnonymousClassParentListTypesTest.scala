package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.{EditorTestUtil, IdeaTestUtil, LightProjectDescriptor}
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ParentListTypesFixture
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ParentListTypesFixture.ExpectedData

/**
 * Documents Java PSI's parent-list API behavior for anonymous classes.
 * Keep it aligned with the platform behavior as a reference when changing the Scala implementation.
 */
class JavaAnonymousClassParentListTypesTest extends LightJavaCodeInsightFixtureTestCase {

  private val Caret = EditorTestUtil.CARET_TAG
  private val JavaObject = "java.lang.Object"

  private lazy val parentListTypesFixture = new ParentListTypesFixture(myFixture, getTestRootDisposable)

  override protected def getProjectDescriptor: LightProjectDescriptor =
    LightJavaCodeInsightFixtureTestCase.JAVA_17

  override protected def setUp(): Unit = {
    super.setUp()
    IdeaTestUtil.setProjectLanguageLevel(getProject, LanguageLevel.JDK_17)
  }

  def testObjectBase(): Unit =
    parentListTypesFixture.assertJavaParentListTypes(
      "Test.java",
      s"Object value = new Object() { int ${Caret}field; };",
      ExpectedData(
        expectedGetSupers = Seq(JavaObject),
        expectedGetSuperTypes = Seq(JavaObject),
        expectedGetExtendsListTypes = Seq.empty,
        expectedGetImplementsListTypes = Seq.empty
      )
    )

  def testClassBase(): Unit =
    parentListTypesFixture.assertJavaParentListTypes(
      "Test.java",
      s"""abstract class Parent {}
         |Object value = new Parent() { int ${Caret}field; };""".stripMargin,
      ExpectedData(
        expectedGetSupers = Seq("Parent"),
        expectedGetSuperTypes = Seq("Parent"),
        expectedGetExtendsListTypes = Seq.empty,
        expectedGetImplementsListTypes = Seq.empty
      )
    )

  // An anonymous class based on an interface has both Object and that interface as effective direct parents.
  def testInterfaceBase(): Unit =
    parentListTypesFixture.assertJavaParentListTypes(
      "Test.java",
      s"""interface Marker {}
         |Object value = new Marker() { int ${Caret}field; };""".stripMargin,
      ExpectedData(
        expectedGetSupers = Seq(JavaObject, "Marker"),
        expectedGetSuperTypes = Seq(JavaObject, "Marker"),
        expectedGetExtendsListTypes = Seq.empty,
        expectedGetImplementsListTypes = Seq.empty
      )
    )

  def testGenericClassBase(): Unit =
    parentListTypesFixture.assertJavaParentListTypes(
      "Test.java",
      s"""abstract class Parent<T> {}
         |Object value = new Parent<String>() { int ${Caret}field; };""".stripMargin,
      ExpectedData(
        expectedGetSupers = Seq("Parent"),
        expectedGetSuperTypes = Seq("Parent<java.lang.String>"),
        expectedGetExtendsListTypes = Seq.empty,
        expectedGetImplementsListTypes = Seq.empty
      )
    )

  def testGenericInterfaceBase(): Unit =
    parentListTypesFixture.assertJavaParentListTypes(
      "Test.java",
      s"""interface Marker<T> {}
         |Object value = new Marker<String>() { int ${Caret}field; };""".stripMargin,
      ExpectedData(
        expectedGetSupers = Seq(JavaObject, "Marker"),
        expectedGetSuperTypes = Seq(JavaObject, "Marker<java.lang.String>"),
        expectedGetExtendsListTypes = Seq.empty,
        expectedGetImplementsListTypes = Seq.empty
      )
    )

  // Java can name only one anonymous-class base. Interfaces inherited by that base are not direct parents.
  def testGenericClassBaseWithInheritedInterfaces(): Unit =
    parentListTypesFixture.assertJavaParentListTypes(
      "Test.java",
      s"""interface First<T> {}
         |interface Second<T> {}
         |abstract class Parent<T> implements First<T>, Second<Long> {}
         |Object value = new Parent<String>() { int ${Caret}field; };""".stripMargin,
      ExpectedData(
        expectedGetSupers = Seq("Parent"),
        expectedGetSuperTypes = Seq("Parent<java.lang.String>"),
        expectedGetExtendsListTypes = Seq.empty,
        expectedGetImplementsListTypes = Seq.empty
      )
    )
}
