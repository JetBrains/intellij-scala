package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ParentListTypesFixture.ExpectedData
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Assert.assertTrue

abstract class ScTypeDefinitionImplTest extends ScalaFixtureTestCase {

  private val JavaObject = "java.lang.Object"
  private val ScalaProduct = "scala.Product"

  private lazy val parentListTypesFixture = new ParentListTypesFixture(myFixture, getTestRootDisposable)

  protected def assertCaseClassWithSyntheticInterfaces(serializableFqn: String): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"case class ${CARET}Foo()",
      ExpectedData(
        expectedGetSupers = Seq(ScalaProduct, serializableFqn, JavaObject),
        expectedGetSuperTypes = Seq(ScalaProduct, serializableFqn, JavaObject),
        expectedGetExtendsListTypes = Seq.empty,
        expectedGetImplementsListTypes = Seq(ScalaProduct, serializableFqn)
      )
    )

  protected def assertCaseClassWithExplicitParents(serializable: String): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""class Parent[A]
         |trait Marker[A]
         |case class ${CARET}Foo() extends Parent[String] with Marker[String]""".stripMargin,
      ExpectedData(
        expectedGetSupers = Seq("Parent", "Marker", ScalaProduct, serializable),
        expectedGetSuperTypes = Seq("Parent<java.lang.String>", "Marker<java.lang.String>", ScalaProduct, serializable),
        expectedGetExtendsListTypes = Seq("Parent<java.lang.String>"),
        expectedGetImplementsListTypes = Seq("Marker<java.lang.String>", ScalaProduct, serializable)
      )
    )

  protected def assertCaseObjectWithSyntheticInterfaces(serializable: String): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"case object ${CARET}Foo",
      ExpectedData(
        expectedGetSupers = Seq(ScalaProduct, serializable, JavaObject),
        expectedGetSuperTypes = Seq(ScalaProduct, serializable, JavaObject),
        expectedGetExtendsListTypes = Seq.empty,
        expectedGetImplementsListTypes = Seq(ScalaProduct, serializable)
      )
    )

  private def assertFakeCompanionModuleExists(
    className: String,
    fakeCompanionModuleQualifiedName: Option[String]
  ): Unit = {
    val clazz = ScalaPsiManager.instance(getProject).getClassesByName(className, GlobalSearchScope.fileScope(getFile)).head.asInstanceOf[ScClass]
    clazz.fakeCompanionModule match {
      case Some(fakeCompanionModule) =>

        assertTrue(s"No fake companion module was expected for $clazz, but one was found anyway",
          fakeCompanionModuleQualifiedName.nonEmpty)

        val expected = fakeCompanionModuleQualifiedName.get
        val actual = fakeCompanionModule.qualifiedName

        assertTrue(s"Fake companion module with FQN $expected was expected for $clazz, but got $actual",
          actual == expected)

      case _ =>
        assertTrue(
          s"""Fake companion module with FQN ${fakeCompanionModuleQualifiedName.getOrElse("")} was expected for $clazz,
             |but no fake companion module was found (by any name)""".stripMargin,
          fakeCompanionModuleQualifiedName.isEmpty)
    }
  }

  def testFakeCompanionModule_Class(): Unit = {
    myFixture.configureByText("Foo.scala", "class Foo")
    assertFakeCompanionModuleExists("Foo", None)
  }

  def testFakeCompanionModule_ClassWithCompanionObject(): Unit = {
    myFixture.configureByText("Foo.scala", "class Foo; object Foo")
    assertFakeCompanionModuleExists("Foo", None)
  }

  def testFakeCompanionModule_ImplicitClass(): Unit = {
    myFixture.configureByText("Foo.scala",
      """object Scope {
        |  implicit class Foo(val d: Double)
        |}""".stripMargin)
    assertFakeCompanionModuleExists("Foo", None)
  }

  def testFakeCompanionModule_ImplicitAnyValClass(): Unit = {
    myFixture.configureByText("Foo.scala",
      """object Scope {
        |  implicit class Foo(val d: Double) extends AnyVal
        |}""".stripMargin)
    assertFakeCompanionModuleExists("Foo", Some("Scope.Foo"))
  }

  def testFakeCompanionModule_ImplicitAnyValClassInPackageObject(): Unit = {
    myFixture.configureByText("Foo.scala",
      """package object Scope {
        |  implicit class Foo(val d: Double) extends AnyVal
        |}""".stripMargin)
    assertFakeCompanionModuleExists("Foo", Some("Scope.package$Foo"))
  }

  def testParentListTypes_NoExplicitParents(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"class ${CARET}Foo",
      ExpectedData(Seq(JavaObject), Seq(JavaObject), Seq.empty, Seq.empty)
    )

  def testParentListTypes_ExplicitSuperclass(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""class Parent
         |class ${CARET}Foo extends Parent""".stripMargin,
      ExpectedData(Seq("Parent"), Seq("Parent"), Seq("Parent"), Seq.empty)
    )

  def testParentListTypes_ExplicitTraits(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""trait First
         |trait Second
         |class ${CARET}Foo extends First with Second""".stripMargin,
      ExpectedData(
        Seq("First", "Second", JavaObject),
        Seq("First", "Second", JavaObject),
        Seq.empty,
        Seq("First", "Second")
      )
    )

  def testParentListTypes_ClassExtendingTraitWithSuperTrait(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""trait BaseTrait
         |trait ChildA extends BaseTrait
         |class ${CARET}ChildB extends ChildA""".stripMargin,
      ExpectedData(Seq("ChildA", JavaObject), Seq("ChildA", JavaObject), Seq.empty, Seq("ChildA"))
    )

  def testParentListTypes_ClassExtendingTraitWithSuperClass(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""class BaseClass
         |trait ChildA extends BaseClass
         |class ${CARET}ChildB extends ChildA""".stripMargin,
      ExpectedData(Seq("ChildA", JavaObject), Seq("ChildA", JavaObject), Seq.empty, Seq("ChildA"))
    )

  def testParentListTypes_MixedParentsWithTypeArguments(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""class Parent[A]
         |trait First[A]
         |trait Second[A]
         |class ${CARET}Foo extends Parent[String] with First[String] with Second[java.lang.Long]""".stripMargin,
      ExpectedData(
        Seq("Parent", "First", "Second"),
        Seq("Parent<java.lang.String>", "First<java.lang.String>", "Second<java.lang.Long>"),
        Seq("Parent<java.lang.String>"),
        Seq("First<java.lang.String>", "Second<java.lang.Long>")
      )
    )

  def testParentListTypes_TypeArgumentsThroughAliases(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""class Parent[A]
         |trait Marker[A]
         |object Aliases {
         |  type StringParent = Parent[String]
         |  type StringMarker = Marker[String]
         |}
         |class ${CARET}Foo extends Aliases.StringParent with Aliases.StringMarker""".stripMargin,
      ExpectedData(
        Seq("Parent", "Marker"),
        Seq("Parent<java.lang.String>", "Marker<java.lang.String>"),
        Seq("Parent<java.lang.String>"),
        Seq("Marker<java.lang.String>")
      )
    )

  def testParentListTypes_ExplicitRootParent(): Unit = {
    parentListTypesFixture.assertParentListTypes(
      s"class ${CARET}Foo extends AnyRef",
      ExpectedData(Seq(JavaObject), Seq(JavaObject), Seq(JavaObject), Seq.empty)
    )
    parentListTypesFixture.assertParentListTypes(
      s"class ${CARET}Foo extends java.lang.Object",
      ExpectedData(Seq(JavaObject), Seq(JavaObject), Seq(JavaObject), Seq.empty)
    )
  }

  def testParentListTypes_Trait(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""trait Parent
         |trait ${CARET}Foo extends Parent""".stripMargin,
      ExpectedData(Seq("Parent", JavaObject), Seq("Parent", JavaObject), Seq.empty, Seq("Parent"))
    )

  def testParentListTypes_Object(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""class Parent
         |trait Marker
         |object ${CARET}Foo extends Parent with Marker""".stripMargin,
      ExpectedData(Seq("Parent", "Marker"), Seq("Parent", "Marker"), Seq("Parent"), Seq("Marker"))
    )

  def testParentListTypes_CaseClassWithSyntheticInterfaces(): Unit =
    assertCaseClassWithSyntheticInterfaces("java.io.Serializable")

  def testParentListTypes_CaseClassWithExplicitParents(): Unit =
    assertCaseClassWithExplicitParents("java.io.Serializable")

  def testParentListTypes_JavaParents(): Unit = {
    myFixture.addFileToProject("JavaParent.java", "public class JavaParent<T> {}")
    myFixture.addFileToProject("JavaMarker.java", "public interface JavaMarker<T> {}")

    parentListTypesFixture.assertParentListTypes(
      s"class ${CARET}Foo extends JavaParent[String] with JavaMarker[String]",
      ExpectedData(
        Seq("JavaParent", "JavaMarker"),
        Seq("JavaParent<java.lang.String>", "JavaMarker<java.lang.String>"),
        Seq("JavaParent<java.lang.String>"),
        Seq("JavaMarker<java.lang.String>")
      )
    )
  }

  def testParentListTypes_CaseObjectWithSyntheticInterfaces(): Unit =
    assertCaseObjectWithSyntheticInterfaces("java.io.Serializable")

  def testParentListTypes_ValueClass(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"class ${CARET}Foo(val value: Int) extends AnyVal",
      ExpectedData(Seq("scala.AnyVal"), Seq(JavaObject), Seq(JavaObject), Seq.empty)
    )

  def testParentListTypes_UniversalTrait(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"trait ${CARET}Foo extends Any",
      ExpectedData(Seq(JavaObject), Seq(JavaObject), Seq(JavaObject), Seq.empty)
    )

  def testParentListTypes_PackageObject(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"package object ${CARET}foo",
      ExpectedData(Seq(JavaObject), Seq(JavaObject), Seq.empty, Seq.empty)
    )

  def testParentListTypes_InjectedInterfaceWithoutWrittenParents(): Unit =
    parentListTypesFixture.assertParentListTypesWithInjectedSupers(
      s"""trait SyntheticMarker
         |class ${CARET}Foo""".stripMargin,
      injectedSupers = Seq("SyntheticMarker"),
      expected = ExpectedData(
        Seq("SyntheticMarker", JavaObject),
        Seq("SyntheticMarker", JavaObject),
        Seq.empty,
        Seq("SyntheticMarker")
      )
    )

  def testParentListTypes_InjectedInterfaceWithWrittenParents(): Unit =
    parentListTypesFixture.assertParentListTypesWithInjectedSupers(
      s"""class Parent
         |trait WrittenMarker
         |trait SyntheticMarker[A]
         |class ${CARET}Foo extends Parent with WrittenMarker""".stripMargin,
      injectedSupers = Seq("SyntheticMarker[String]"),
      expected = ExpectedData(
        Seq("Parent", "WrittenMarker", "SyntheticMarker"),
        Seq("Parent", "WrittenMarker", "SyntheticMarker<java.lang.String>"),
        Seq("Parent"),
        Seq("WrittenMarker", "SyntheticMarker<java.lang.String>")
      )
    )
}

class ScTypeDefinitionImplTest_Scala_2_12 extends ScTypeDefinitionImplTest {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_2_12

  override def testParentListTypes_CaseClassWithSyntheticInterfaces(): Unit =
    assertCaseClassWithSyntheticInterfaces("scala.Serializable")

  override def testParentListTypes_CaseClassWithExplicitParents(): Unit =
    assertCaseClassWithExplicitParents("scala.Serializable")

  override def testParentListTypes_CaseObjectWithSyntheticInterfaces(): Unit =
    assertCaseObjectWithSyntheticInterfaces("scala.Serializable")
}

class ScTypeDefinitionImplTest_Scala_2_13 extends ScTypeDefinitionImplTest {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_2_13
}

class ScTypeDefinitionImplTest_Scala_3 extends ScTypeDefinitionImplTest {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_3
}
