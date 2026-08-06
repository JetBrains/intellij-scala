package org.jetbrains.plugins.scala.lang.psi.impl.expr

import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ParentListTypesFixture
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ParentListTypesFixture.ExpectedData
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

abstract class ScNewTemplateDefinitionImplTest extends ScalaFixtureTestCase {

  private val JavaObject = "java.lang.Object"
  private val AnonymousBody = s"{ val ${CARET}field = 0 }"

  private lazy val parentListTypesFixture = new ParentListTypesFixture(myFixture, getTestRootDisposable)

  // Matches Java's `new Object() {}`: the implicit root is effective, but anonymous parent-list arrays are empty.
  def testParentListTypes_NoExplicitParents(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"val value = new $AnonymousBody",
      ExpectedData(
        expectedGetSupers = Seq(JavaObject),
        expectedGetSuperTypes = Seq(JavaObject),
        expectedGetExtendsListTypes = Seq.empty,
        expectedGetImplementsListTypes = Seq.empty
      )
    )

  // Matches a Java anonymous subclass: the named base is effective, but is not exposed as an extends-list type.
  def testParentListTypes_ExplicitAbstractSuperclass(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""abstract class Parent
         |val value = new Parent $AnonymousBody""".stripMargin,
      ExpectedData(
        expectedGetSupers = Seq("Parent"),
        expectedGetSuperTypes = Seq("Parent"),
        expectedGetExtendsListTypes = Seq.empty,
        expectedGetImplementsListTypes = Seq.empty
      )
    )

  // Java can name only one interface base; Scala retains both, after Object, in its effective super APIs.
  def testParentListTypes_ExplicitTraits(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""trait First
         |trait Second
         |val value = new First with Second $AnonymousBody""".stripMargin,
      ExpectedData(
        expectedGetSupers = Seq(JavaObject, "First", "Second"),
        expectedGetSuperTypes = Seq(JavaObject, "First", "Second"),
        expectedGetExtendsListTypes = Seq.empty,
        expectedGetImplementsListTypes = Seq.empty
      )
    )

  // Java cannot add interfaces beside an anonymous class base; Scala keeps all three effective direct parents.
  def testParentListTypes_MixedParentsWithTypeArguments(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""class Parent[A]
         |trait First[A]
         |trait Second[A]
         |val value = new Parent[String] with First[String] with Second[java.lang.Long] $AnonymousBody""".stripMargin,
      ExpectedData(
        expectedGetSupers = Seq("Parent", "First", "Second"),
        expectedGetSuperTypes = Seq("Parent<java.lang.String>", "First<java.lang.String>", "Second<java.lang.Long>"),
        expectedGetExtendsListTypes = Seq.empty,
        expectedGetImplementsListTypes = Seq.empty
      )
    )

  // Java has no type aliases; Scala expands them in the effective super APIs while keeping anonymous parent lists empty.
  def testParentListTypes_TypeArgumentsThroughAliases(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""class Parent[A]
         |trait Marker[A]
         |object Aliases {
         |  type StringParent = Parent[String]
         |  type StringMarker = Marker[String]
         |}
         |val value = new Aliases.StringParent with Aliases.StringMarker $AnonymousBody""".stripMargin,
      ExpectedData(
        expectedGetSupers = Seq("Parent", "Marker"),
        expectedGetSuperTypes = Seq("Parent<java.lang.String>", "Marker<java.lang.String>"),
        expectedGetExtendsListTypes = Seq.empty,
        expectedGetImplementsListTypes = Seq.empty
      )
    )

  // Like Java's explicit Object base, Scala exposes the root only through the effective super APIs.
  def testParentListTypes_ExplicitRootParent(): Unit = {
    parentListTypesFixture.assertParentListTypes(
      s"val value = new AnyRef $AnonymousBody",
      ExpectedData(
        expectedGetSupers = Seq(JavaObject),
        expectedGetSuperTypes = Seq(JavaObject),
        expectedGetExtendsListTypes = Seq.empty,
        expectedGetImplementsListTypes = Seq.empty
      )
    )
    parentListTypesFixture.assertParentListTypes(
      s"val value = new java.lang.Object $AnonymousBody",
      ExpectedData(
        expectedGetSupers = Seq(JavaObject),
        expectedGetSuperTypes = Seq(JavaObject),
        expectedGetExtendsListTypes = Seq.empty,
        expectedGetImplementsListTypes = Seq.empty
      )
    )
  }

  // Java anonymous syntax cannot mix class and interface bases; Scala keeps both as effective direct parents.
  def testParentListTypes_JavaParents(): Unit = {
    myFixture.addFileToProject("JavaParent.java", "public class JavaParent<T> {}")
    myFixture.addFileToProject("JavaMarker.java", "public interface JavaMarker<T> {}")

    parentListTypesFixture.assertParentListTypes(
      s"val value = new JavaParent[String] with JavaMarker[String] $AnonymousBody",
      ExpectedData(
        expectedGetSupers = Seq("JavaParent", "JavaMarker"),
        expectedGetSuperTypes = Seq("JavaParent<java.lang.String>", "JavaMarker<java.lang.String>"),
        expectedGetExtendsListTypes = Seq.empty,
        expectedGetImplementsListTypes = Seq.empty
      )
    )
  }
}

class ScNewTemplateDefinitionImplTest_Scala_2_12 extends ScNewTemplateDefinitionImplTest {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_2_12
}

class ScNewTemplateDefinitionImplTest_Scala_2_13 extends ScNewTemplateDefinitionImplTest {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_2_13
}

class ScNewTemplateDefinitionImplTest_Scala_3 extends ScNewTemplateDefinitionImplTest {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_3
}
