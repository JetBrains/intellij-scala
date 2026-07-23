package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.{EditorTestUtil, IdeaTestUtil, LightProjectDescriptor}
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import ParentListTypesFixture.ExpectedData

/**
 * Documents Java PSI's parent-list API behavior, including compiler-synthesized enum and record parents.
 * Keep it aligned with the platform behavior as a reference when changing the Scala implementation.
 */
class JavaPsiClassParentListTypesTest extends LightJavaCodeInsightFixtureTestCase {

  private val Caret = EditorTestUtil.CARET_TAG
  private val JavaObject = "java.lang.Object"

  private lazy val parentListTypesFixture = new ParentListTypesFixture(myFixture, getTestRootDisposable)

  override protected def getProjectDescriptor: LightProjectDescriptor =
    LightJavaCodeInsightFixtureTestCase.JAVA_17

  override protected def setUp(): Unit = {
    super.setUp()
    IdeaTestUtil.setProjectLanguageLevel(getProject, LanguageLevel.JDK_17)
  }

  def testClassWithoutExplicitParents(): Unit =
    parentListTypesFixture.assertJavaParentListTypes(
      "Test.java",
      s"public class ${Caret}PlainClass {}",
      ExpectedData(
        expectedGetSupers = Seq(JavaObject),
        expectedGetSuperTypes = Seq(JavaObject),
        expectedGetExtendsListTypes = Seq.empty,
        expectedGetImplementsListTypes = Seq.empty
      )
    )

  def testClassWithGenericParentAndInterface(): Unit =
    parentListTypesFixture.assertJavaParentListTypes(
      "Test.java",
      s"""interface Marker<T> {}
         |class Parent<T> {}
         |class ${Caret}Child extends Parent<String> implements Marker<String> {}""".stripMargin,
      ExpectedData(
        expectedGetSupers = Seq("Parent", "Marker"),
        expectedGetSuperTypes = Seq("Parent<java.lang.String>", "Marker<java.lang.String>"),
        expectedGetExtendsListTypes = Seq("Parent<java.lang.String>"),
        expectedGetImplementsListTypes = Seq("Marker<java.lang.String>")
      )
    )

  // Like a Scala trait, a Java interface keeps its declared parent in `getExtendsListTypes`.
  // Unlike a Scala trait, Java `getSupers` adds `java.lang.Object`, while `getSuperTypes` omits it.
  def testInterfaceWithGenericParent(): Unit =
    parentListTypesFixture.assertJavaParentListTypes(
      "Test.java",
      s"""interface Parent<T> {}
         |interface ${Caret}Child extends Parent<String> {}""".stripMargin,
      ExpectedData(
        expectedGetSupers = Seq(JavaObject, "Parent"),
        expectedGetSuperTypes = Seq("Parent<java.lang.String>"),
        expectedGetExtendsListTypes = Seq("Parent<java.lang.String>"),
        expectedGetImplementsListTypes = Seq.empty
      )
    )

  // Unlike Scala case-class synthetic interfaces, Java exposes the synthetic `Enum<E>` parent through `getExtendsListTypes`.
  def testEnum(): Unit =
    parentListTypesFixture.assertJavaParentListTypes(
      "Test.java",
      s"enum ${Caret}PlainEnum { Value }",
      ExpectedData(
        expectedGetSupers = Seq("java.lang.Enum"),
        expectedGetSuperTypes = Seq("java.lang.Enum<PlainEnum>"),
        expectedGetExtendsListTypes = Seq("java.lang.Enum<PlainEnum>"),
        expectedGetImplementsListTypes = Seq.empty
      )
    )

  // The synthetic `Enum<E>` parent is in `getExtendsListTypes`; the written interface is in `getImplementsListTypes`.
  def testEnumWithInterface(): Unit =
    parentListTypesFixture.assertJavaParentListTypes(
      "Test.java",
      s"""interface Marker {}
         |enum ${Caret}EnumWithMarker implements Marker { Value }""".stripMargin,
      ExpectedData(
        expectedGetSupers = Seq("java.lang.Enum", "Marker"),
        expectedGetSuperTypes = Seq("java.lang.Enum<EnumWithMarker>", "Marker"),
        expectedGetExtendsListTypes = Seq("java.lang.Enum<EnumWithMarker>"),
        expectedGetImplementsListTypes = Seq("Marker")
      )
    )

  // Unlike Scala's resolved synthetic parents, the Java record's implicit `java.lang.Record` is omitted from `getSupers` here.
  def testRecord(): Unit =
    parentListTypesFixture.assertJavaParentListTypes(
      "Test.java",
      s"public record ${Caret}PlainRecord(int value) {}",
      ExpectedData(
        expectedGetSupers = Seq.empty,
        expectedGetSuperTypes = Seq("java.lang.Record"),
        expectedGetExtendsListTypes = Seq("java.lang.Record"),
        expectedGetImplementsListTypes = Seq.empty
      )
    )

  // `getSupers` reports the written interface but omits the implicit `java.lang.Record` parent reported by the type APIs.
  def testRecordWithInterface(): Unit =
    parentListTypesFixture.assertJavaParentListTypes(
      "Test.java",
      s"""interface Marker {}
         |record ${Caret}RecordWithMarker(int value) implements Marker {}""".stripMargin,
      ExpectedData(
        expectedGetSupers = Seq("Marker"),
        expectedGetSuperTypes = Seq("java.lang.Record", "Marker"),
        expectedGetExtendsListTypes = Seq("java.lang.Record"),
        expectedGetImplementsListTypes = Seq("Marker")
      )
    )
}
