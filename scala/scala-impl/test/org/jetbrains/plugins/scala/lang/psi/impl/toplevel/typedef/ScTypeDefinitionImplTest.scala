package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiClassType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.Assert.{assertEquals, assertTrue}

abstract class ScTypeDefinitionImplTest extends ScalaFixtureTestCase {

  import ScTypeDefinitionImplTest._

  private val JavaObject = "java.lang.Object"
  private val ScalaProduct = "scala.Product"

  private lazy val parentListTypesFixture = new ParentListTypesFixture(myFixture, getTestRootDisposable)

  protected def assertCaseClassWithSyntheticInterfaces(serializableFqn: String): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"case class ${CARET}Foo()",
      expectedSuperTypes = Seq(ScalaProduct, serializableFqn, JavaObject),
      expectedExtends = Seq.empty,
      expectedImplements = Seq(ScalaProduct, serializableFqn)
    )

  protected def assertCaseClassWithExplicitParents(serializable: String): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""class Parent[A]
         |trait Marker[A]
         |case class ${CARET}Foo() extends Parent[String] with Marker[String]""".stripMargin,
      expectedSuperTypes = Seq("Parent<java.lang.String>", "Marker<java.lang.String>", ScalaProduct, serializable),
      expectedExtends = Seq("Parent<java.lang.String>"),
      expectedImplements = Seq("Marker<java.lang.String>", ScalaProduct, serializable)
    )

  protected def assertCaseObjectWithSyntheticInterfaces(serializable: String): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"case object ${CARET}Foo",
      expectedSuperTypes = Seq(ScalaProduct, serializable, JavaObject),
      expectedExtends = Seq.empty,
      expectedImplements = Seq(ScalaProduct, serializable)
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
      expectedSuperTypes = Seq(JavaObject),
      expectedExtends = Seq.empty,
      expectedImplements = Seq.empty
    )

  def testParentListTypes_ExplicitSuperclass(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""class Parent
         |class ${CARET}Foo extends Parent""".stripMargin,
      expectedSuperTypes = Seq("Parent"),
      expectedExtends = Seq("Parent"),
      expectedImplements = Seq.empty
    )

  def testParentListTypes_ExplicitTraits(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""trait First
         |trait Second
         |class ${CARET}Foo extends First with Second""".stripMargin,
      expectedSuperTypes = Seq("First", "Second", JavaObject),
      expectedExtends = Seq.empty,
      expectedImplements = Seq("First", "Second")
    )

  def testParentListTypes_MixedParentsWithTypeArguments(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""class Parent[A]
         |trait First[A]
         |trait Second[A]
         |class ${CARET}Foo extends Parent[String] with First[String] with Second[java.lang.Long]""".stripMargin,
      expectedSuperTypes = Seq("Parent<java.lang.String>", "First<java.lang.String>", "Second<java.lang.Long>"),
      expectedExtends = Seq("Parent<java.lang.String>"),
      expectedImplements = Seq("First<java.lang.String>", "Second<java.lang.Long>")
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
      expectedSuperTypes = Seq("Parent<java.lang.String>", "Marker<java.lang.String>"),
      expectedExtends = Seq("Parent<java.lang.String>"),
      expectedImplements = Seq("Marker<java.lang.String>")
    )

  def testParentListTypes_ExplicitRootParent(): Unit = {
    parentListTypesFixture.assertParentListTypes(
      s"class ${CARET}Foo extends AnyRef",
      expectedSuperTypes = Seq(JavaObject),
      expectedExtends = Seq(JavaObject),
      expectedImplements = Seq.empty
    )
    parentListTypesFixture.assertParentListTypes(
      s"class ${CARET}Foo extends java.lang.Object",
      expectedSuperTypes = Seq(JavaObject),
      expectedExtends = Seq(JavaObject),
      expectedImplements = Seq.empty
    )
  }

  def testParentListTypes_Trait(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""trait Parent
         |trait ${CARET}Foo extends Parent""".stripMargin,
      expectedSuperTypes = Seq("Parent", JavaObject),
      expectedExtends = Seq.empty,
      expectedImplements = Seq("Parent")
    )

  def testParentListTypes_Object(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"""class Parent
         |trait Marker
         |object ${CARET}Foo extends Parent with Marker""".stripMargin,
      expectedSuperTypes = Seq("Parent", "Marker"),
      expectedExtends = Seq("Parent"),
      expectedImplements = Seq("Marker")
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
      expectedSuperTypes = Seq("JavaParent<java.lang.String>", "JavaMarker<java.lang.String>"),
      expectedExtends = Seq("JavaParent<java.lang.String>"),
      expectedImplements = Seq("JavaMarker<java.lang.String>")
    )
  }

  def testParentListTypes_CaseObjectWithSyntheticInterfaces(): Unit =
    assertCaseObjectWithSyntheticInterfaces("java.io.Serializable")

  def testParentListTypes_ValueClass(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"class ${CARET}Foo(val value: Int) extends AnyVal",
      expectedSuperTypes = Seq(JavaObject),
      expectedExtends = Seq(JavaObject),
      expectedImplements = Seq.empty
    )

  def testParentListTypes_UniversalTrait(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"trait ${CARET}Foo extends Any",
      expectedSuperTypes = Seq(JavaObject),
      expectedExtends = Seq(JavaObject),
      expectedImplements = Seq.empty
    )

  def testParentListTypes_PackageObject(): Unit =
    parentListTypesFixture.assertParentListTypes(
      s"package object ${CARET}foo",
      expectedSuperTypes = Seq(JavaObject),
      expectedExtends = Seq.empty,
      expectedImplements = Seq.empty
    )

  def testParentListTypes_InjectedInterfaceWithoutWrittenParents(): Unit =
    parentListTypesFixture.assertParentListTypesWithInjectedSupers(
      s"""trait SyntheticMarker
         |class ${CARET}Foo""".stripMargin,
      injectedSupers = Seq("SyntheticMarker"),
      expectedSuperTypes = Seq("SyntheticMarker", JavaObject),
      expectedExtends = Seq.empty,
      expectedImplements = Seq("SyntheticMarker")
    )

  def testParentListTypes_InjectedInterfaceWithWrittenParents(): Unit =
    parentListTypesFixture.assertParentListTypesWithInjectedSupers(
      s"""class Parent
         |trait WrittenMarker
         |trait SyntheticMarker[A]
         |class ${CARET}Foo extends Parent with WrittenMarker""".stripMargin,
      injectedSupers = Seq("SyntheticMarker[String]"),
      expectedSuperTypes = Seq("Parent", "WrittenMarker", "SyntheticMarker<java.lang.String>"),
      expectedExtends = Seq("Parent"),
      expectedImplements = Seq("WrittenMarker", "SyntheticMarker<java.lang.String>")
    )
}

class ScTypeDefinitionImplTest_Scala_2_10 extends ScTypeDefinitionImplTest {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_2_10

  override def testParentListTypes_CaseClassWithSyntheticInterfaces(): Unit =
    assertCaseClassWithSyntheticInterfaces("scala.Serializable")

  override def testParentListTypes_CaseClassWithExplicitParents(): Unit =
    assertCaseClassWithExplicitParents("scala.Serializable")

  override def testParentListTypes_CaseObjectWithSyntheticInterfaces(): Unit =
    assertCaseObjectWithSyntheticInterfaces("scala.Serializable")
}

class ScTypeDefinitionImplTest_Scala_2_11 extends ScTypeDefinitionImplTest {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_2_11

  override def testParentListTypes_CaseClassWithSyntheticInterfaces(): Unit =
    assertCaseClassWithSyntheticInterfaces("scala.Serializable")

  override def testParentListTypes_CaseClassWithExplicitParents(): Unit =
    assertCaseClassWithExplicitParents("scala.Serializable")

  override def testParentListTypes_CaseObjectWithSyntheticInterfaces(): Unit =
    assertCaseObjectWithSyntheticInterfaces("scala.Serializable")
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

object ScTypeDefinitionImplTest {

  private final class ParentListTypesFixture(fixture: CodeInsightTestFixture, testRootDisposable: Disposable) {

    def assertParentListTypes(
      @Language("Scala 3") scalaText: String,
      expectedSuperTypes: Seq[String],
      expectedExtends: Seq[String],
      expectedImplements: Seq[String]
    ): Unit = {
      val definition = configureAndFindTypeDefinition(scalaText)

      assertParentListTypes(definition, expectedSuperTypes, expectedExtends, expectedImplements)
    }

    def assertParentListTypesWithInjectedSupers(
      @Language("Scala 3") scalaText: String,
      injectedSupers: Seq[String],
      expectedSuperTypes: Seq[String],
      expectedExtends: Seq[String],
      expectedImplements: Seq[String]
    ): Unit = {
      val definition = configureAndFindTypeDefinition(scalaText)
      val injector = new SyntheticMembersInjector {
        override def injectSupers(source: ScTypeDefinition): Seq[String] =
          if (source == definition) injectedSupers else Seq.empty
      }
      ApplicationManager.getApplication.getExtensionArea
        .getExtensionPoint(SyntheticMembersInjector.EP_NAME)
        .registerExtension(injector, testRootDisposable)

      assertParentListTypes(definition, expectedSuperTypes, expectedExtends, expectedImplements)
    }

    private def configureAndFindTypeDefinition(@Language("Scala 3") scalaText: String): ScTypeDefinition = {
      fixture.configureByText("Test.scala", scalaText)
      findTypeDefinitionAtCaret()
    }

    private def assertParentListTypes(
      definition: ScTypeDefinition,
      expectedSuperTypes: Seq[String],
      expectedExtends: Seq[String],
      expectedImplements: Seq[String]
    ): Unit = {
      assertClassTypes("super types", definition, expectedSuperTypes, definition.getSuperTypes)
      assertClassTypes("extends", definition, expectedExtends, definition.getExtendsListTypes)
      assertClassTypes("implements", definition, expectedImplements, definition.getImplementsListTypes)
    }

    private def findTypeDefinitionAtCaret(): ScTypeDefinition = {
      val definition = PsiTreeUtil.getParentOfType(
        fixture.getElementAtCaret,
        classOf[ScTypeDefinition],
        false
      )
      Option(definition).getOrElse(throw new AssertionError("No type definition found at the caret"))
    }

    private def assertClassTypes(
      listName: String,
      definition: ScTypeDefinition,
      expected: Seq[String],
      actual: Array[PsiClassType]
    ): Unit = {
      val actualCanonicalTexts = actual.map(_.getCanonicalText).toSeq
      assertEquals(
        s"Unexpected $listName list types for `${definition.getText}`",
        expected,
        actualCanonicalTexts
      )
    }
  }
}
