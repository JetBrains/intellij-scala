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
      ExpectedData(
        expectedGetSupers = Seq(JavaObject),
        expectedGetSuperTypes = Seq(JavaObject),
        expectedGetExtendsListTypes = Seq.empty,
        expectedGetImplementsListTypes = Seq.empty
      )
    )

  def testParentListTypes_ExplicitSuperclass(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""class Parent
         |class ${CARET}Foo extends Parent""".stripMargin,
      ExpectedData(
        expectedGetSupers = Seq("Parent"),
        expectedGetSuperTypes = Seq("Parent"),
        expectedGetExtendsListTypes = Seq("Parent"),
        expectedGetImplementsListTypes = Seq.empty
      )
    )

  def testParentListTypes_ExplicitTraits(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""trait First
         |trait Second
         |class ${CARET}Foo extends First with Second""".stripMargin,
      ExpectedData(
        expectedGetSupers = Seq("First", "Second", JavaObject),
        expectedGetSuperTypes = Seq("First", "Second", JavaObject),
        expectedGetExtendsListTypes = Seq.empty,
        expectedGetImplementsListTypes = Seq("First", "Second")
      )
    )

  def testParentListTypes_ClassExtendingTraitWithSuperTrait(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""trait BaseTrait
         |trait ChildA extends BaseTrait
         |class ${CARET}ChildB extends ChildA""".stripMargin,
      ExpectedData(
        expectedGetSupers = Seq("ChildA", JavaObject),
        expectedGetSuperTypes = Seq("ChildA", JavaObject),
        expectedGetExtendsListTypes = Seq.empty,
        expectedGetImplementsListTypes = Seq("ChildA")
      )
    )

  def testParentListTypes_ClassExtendingTraitWithSuperClass(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""class BaseClass
         |trait ChildA extends BaseClass
         |class ${CARET}ChildB extends ChildA""".stripMargin,
      ExpectedData(
        expectedGetSupers = Seq("ChildA", JavaObject),
        expectedGetSuperTypes = Seq("ChildA", JavaObject),
        expectedGetExtendsListTypes = Seq.empty,
        expectedGetImplementsListTypes = Seq("ChildA")
      )
    )

  // Known source-PSI/JVM-shape mismatch (SCL-25714): source PSI retains a trait's Java superclass even though the
  // emitted trait interface cannot extend that class.
  def testParentListTypes_TraitExtendingJavaClass(): Unit = {
    myFixture.addFileToProject("JavaBase.java", "public abstract class JavaBase {}")

    parentListTypesFixture.assertParentListTypes(
      s"trait ${CARET}Foo extends JavaBase",
      ExpectedData(
        expectedGetSupers = Seq("JavaBase"),
        expectedGetSuperTypes = Seq("JavaBase"),
        expectedGetExtendsListTypes = Seq("JavaBase"),
        expectedGetImplementsListTypes = Seq.empty
      )
    )
  }

  def testParentListTypes_MixedParentsWithTypeArguments(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""class Parent[A]
         |trait First[A]
         |trait Second[A]
         |class ${CARET}Foo extends Parent[String] with First[String] with Second[java.lang.Long]""".stripMargin,
      ExpectedData(
        expectedGetSupers = Seq("Parent", "First", "Second"),
        expectedGetSuperTypes = Seq("Parent<java.lang.String>", "First<java.lang.String>", "Second<java.lang.Long>"),
        expectedGetExtendsListTypes = Seq("Parent<java.lang.String>"),
        expectedGetImplementsListTypes = Seq("First<java.lang.String>", "Second<java.lang.Long>")
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
        expectedGetSupers = Seq("Parent", "Marker"),
        expectedGetSuperTypes = Seq("Parent<java.lang.String>", "Marker<java.lang.String>"),
        expectedGetExtendsListTypes = Seq("Parent<java.lang.String>"),
        expectedGetImplementsListTypes = Seq("Marker<java.lang.String>")
      )
    )

  def testParentListTypes_ExplicitRootParent(): Unit = {
    parentListTypesFixture.assertParentListTypes(
      s"class ${CARET}Foo extends AnyRef",
      ExpectedData(
        expectedGetSupers = Seq(JavaObject),
        expectedGetSuperTypes = Seq(JavaObject),
        expectedGetExtendsListTypes = Seq(JavaObject),
        expectedGetImplementsListTypes = Seq.empty
      )
    )
    parentListTypesFixture.assertParentListTypes(
      s"class ${CARET}Foo extends java.lang.Object",
      ExpectedData(
        expectedGetSupers = Seq(JavaObject),
        expectedGetSuperTypes = Seq(JavaObject),
        expectedGetExtendsListTypes = Seq(JavaObject),
        expectedGetImplementsListTypes = Seq.empty
      )
    )
  }

  // A Scala trait is exposed as a Java interface, so its direct interface parent belongs to the extends list.
  def testParentListTypes_TraitExtendingScalaTrait(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""trait Parent
         |trait ${CARET}Foo extends Parent""".stripMargin,
      ExpectedData(
        expectedGetSupers = Seq("Parent", JavaObject),
        expectedGetSuperTypes = Seq("Parent", JavaObject),
        expectedGetExtendsListTypes = Seq("Parent"),
        expectedGetImplementsListTypes = Seq.empty
      )
    )

  def testParentListTypes_TraitExtendingJavaInterface(): Unit = {
    myFixture.addFileToProject("JavaParent.java", "public interface JavaParent<T> {}")

    parentListTypesFixture.assertParentListTypes(
      s"trait ${CARET}Foo extends JavaParent[String]",
      ExpectedData(
        expectedGetSupers = Seq("JavaParent", JavaObject),
        expectedGetSuperTypes = Seq("JavaParent<java.lang.String>", JavaObject),
        expectedGetExtendsListTypes = Seq("JavaParent<java.lang.String>"),
        expectedGetImplementsListTypes = Seq.empty
      )
    )
  }

  def testParentListTypes_Object(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""class Parent
         |trait Marker
         |object ${CARET}Foo extends Parent with Marker""".stripMargin,
      ExpectedData(
        expectedGetSupers = Seq("Parent", "Marker"),
        expectedGetSuperTypes = Seq("Parent", "Marker"),
        expectedGetExtendsListTypes = Seq("Parent"),
        expectedGetImplementsListTypes = Seq("Marker")
      )
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
        expectedGetSupers = Seq("JavaParent", "JavaMarker"),
        expectedGetSuperTypes = Seq("JavaParent<java.lang.String>", "JavaMarker<java.lang.String>"),
        expectedGetExtendsListTypes = Seq("JavaParent<java.lang.String>"),
        expectedGetImplementsListTypes = Seq("JavaMarker<java.lang.String>")
      )
    )
  }

  def testParentListTypes_CaseObjectWithSyntheticInterfaces(): Unit =
    assertCaseObjectWithSyntheticInterfaces("java.io.Serializable")

  def testParentListTypes_ValueClass(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"class ${CARET}Foo(val value: Int) extends AnyVal",
      ExpectedData(
        expectedGetSupers = Seq("scala.AnyVal"),
        expectedGetSuperTypes = Seq(JavaObject),
        expectedGetExtendsListTypes = Seq(JavaObject),
        expectedGetImplementsListTypes = Seq.empty
      )
    )

  def testParentListTypes_UniversalTrait(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"trait ${CARET}Foo extends Any",
      ExpectedData(
        expectedGetSupers = Seq(JavaObject),
        expectedGetSuperTypes = Seq(JavaObject),
        expectedGetExtendsListTypes = Seq(JavaObject),
        expectedGetImplementsListTypes = Seq.empty
      )
    )

  def testParentListTypes_PackageObject(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"package object ${CARET}foo",
      ExpectedData(
        expectedGetSupers = Seq(JavaObject),
        expectedGetSuperTypes = Seq(JavaObject),
        expectedGetExtendsListTypes = Seq.empty,
        expectedGetImplementsListTypes = Seq.empty
      )
    )

  def testParentListTypes_InjectedInterfaceWithoutWrittenParents(): Unit =
    parentListTypesFixture.assertParentListTypesWithInjectedSupers(
      s"""trait SyntheticMarker
         |class ${CARET}Foo""".stripMargin,
      injectedSupers = Seq("SyntheticMarker"),
      expected = ExpectedData(
        expectedGetSupers = Seq("SyntheticMarker", JavaObject),
        expectedGetSuperTypes = Seq("SyntheticMarker", JavaObject),
        expectedGetExtendsListTypes = Seq.empty,
        expectedGetImplementsListTypes = Seq("SyntheticMarker")
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
        expectedGetSupers = Seq("Parent", "WrittenMarker", "SyntheticMarker"),
        expectedGetSuperTypes = Seq("Parent", "WrittenMarker", "SyntheticMarker<java.lang.String>"),
        expectedGetExtendsListTypes = Seq("Parent"),
        expectedGetImplementsListTypes = Seq("WrittenMarker", "SyntheticMarker<java.lang.String>")
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
