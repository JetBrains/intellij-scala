package org.jetbrains.plugins.scala.hierarchy

import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx
import com.intellij.ide.hierarchy.`type`.{SubtypesHierarchyTreeStructure, SupertypesHierarchyTreeStructure, TypeHierarchyNodeDescriptor}
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.psi.{JavaPsiFacade, PsiClass}
import com.intellij.psi.search.GlobalSearchScope
import junit.framework.TestCase.assertEquals
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaFileType, ScalaVersion}

class ScalaTypeHierarchyProviderTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_2_13

  private def typeHierarchyTarget(@Language("Scala") code: String): PsiClass = {
    myFixture.configureByText(ScalaFileType.INSTANCE, code)

    val typeHierarchyProvider = new ScalaTypeHierarchyProvider
    val dataContext = EditorUtil.getEditorDataContext(myFixture.getEditor)
    typeHierarchyProvider.getTarget(dataContext)
  }

  private def assertTypeHierarchyTarget(
    @Language("Scala") code: String,
    expectedTargetName: String
  ): Unit = {
    val actualTarget = typeHierarchyTarget(code)
    assertEquals(
      expectedTargetName,
      actualTarget.getName
    )
  }

  def testTargetElement_ReferenceToClass(): Unit = assertTypeHierarchyTarget(
    s"""class Base
       |
       |class Impl extends ${CARET}Base
       |""".stripMargin,
    "Base"
  )

  def testTargetElement_ReferenceToPrimaryConstructor(): Unit = assertTypeHierarchyTarget(
    s"""trait Trait
       |class Base(i: Int)
       |
       |class Impl extends ${CARET}Base(1) with Trait
       |""".stripMargin,
    "Base"
  )

  def testTargetElement_ReferenceToSecondaryConstructor(): Unit = assertTypeHierarchyTarget(
    s"""trait Trait
       |class Base(i: Int) {
       |  def this(s: String) = this(1)
       |}
       |
       |class Impl extends ${CARET}Base("") with Trait
       |""".stripMargin,
    "Base"
  )

  def testTargetElement_ReferenceToTrait(): Unit = assertTypeHierarchyTarget(
    s"""trait Trait
       |
       |class Impl extends ${CARET}Trait
       |""".stripMargin,
    "Trait"
  )

  def testTargetElement_ReferenceToTypeAlias(): Unit = assertTypeHierarchyTarget(
    s"""class Base
       |
       |type Alias = Base
       |
       |class Impl extends ${CARET}Alias
       |""".stripMargin,
    "Base"
  )

  def testTargetElement_MethodReturnType(): Unit = assertTypeHierarchyTarget(
    s"""class MyClass
       |class Outer {
       |  def test: ${CARET}MyClass = 3
       |}
       |""".stripMargin,
    "MyClass"
  )

  def testTargetElement_MethodDefinition(): Unit = assertTypeHierarchyTarget(
    s"""class MyClass
       |class Outer {
       |  def ${CARET}test: MyClass = 3
       |}
       |""".stripMargin,
    // test has no type hierarchy, so take the outer one
    "Outer"
  )

  def testTargetElement_TypeAliasDefinition(): Unit = assertTypeHierarchyTarget(
    s"""class MyClass
       |
       |class Outer {
       |  type ${CARET}Alias = MyClass
       |}
       |""".stripMargin,
    "MyClass"
  )

  def testTargetElement_TypeAliasRightHandSide(): Unit = assertTypeHierarchyTarget(
    s"""class MyClass
       |
       |class Outer {
       |  type Alias = ${CARET}MyClass
       |}
       |""".stripMargin,
    "MyClass"
  )

  def testTargetElement_ObjectDefinition(): Unit = assertTypeHierarchyTarget(
    s"""class Outer {
       |  object ${CARET}Inner
       |}
       |""".stripMargin,
    "Inner$"
  )

  //noinspection ScalaWrongPlatformMethodsUsage
  private def assertDirectSupertypes(
    @Language("Scala") code: String,
    expectedSupertypeNames: Seq[String]
  ): Unit = {
    // This structure builds the Supertypes view in TypeHierarchyBrowser. It delegates to PsiClass.getSupers
    // (except for annotation meta-annotations), so it does not invoke hierarchy providers again.
    // The view filters java.lang.Object from interfaces after this call; these tests assert the raw result.
    val actualSupers = SupertypesHierarchyTreeStructure.getSupers(typeHierarchyTarget(code))
    val actualSupertypeNames = actualSupers.map(_.getQualifiedName).toSeq

    assertEquals(expectedSupertypeNames, actualSupertypeNames)
  }

  def testDirectSupertypes_ClassExtendsClass(): Unit = assertDirectSupertypes(
    s"""class Parent
       |
       |class ${CARET}Child extends Parent
       |""".stripMargin,
    Seq("Parent")
  )

  def testDirectSupertypes_ClassExtendsTrait(): Unit = assertDirectSupertypes(
    s"""trait Parent
       |
       |class ${CARET}Child extends Parent
       |""".stripMargin,
    Seq("Parent", "java.lang.Object")
  )

  def testDirectSupertypes_TraitExtendsTrait(): Unit = assertDirectSupertypes(
    s"""trait Parent
       |
       |trait ${CARET}Child extends Parent
       |""".stripMargin,
    Seq("Parent", "java.lang.Object")
  )

  def testDirectSupertypes_TraitExtendsAbstractClass(): Unit = assertDirectSupertypes(
    s"""abstract class Parent
       |
       |trait ${CARET}Child extends Parent
       |""".stripMargin,
    Seq("Parent")
  )

  private def directSubtypes(psiClass: PsiClass): Seq[PsiClass] = {
    val hierarchy = new SubtypesHierarchyTreeStructure(getProject, psiClass, HierarchyBrowserBaseEx.SCOPE_ALL)
    val children = hierarchy.getChildElements(hierarchy.getBaseDescriptor)

    children.collect {
      case descriptor: TypeHierarchyNodeDescriptor => descriptor.getPsiClass
    }.collect {
      case psiClass: PsiClass => psiClass
    }.toSeq
  }

  private def assertDirectSubtypes(
    targetClassFqn: String,
    expectedSubtypeNames: Seq[String]
  ): Unit = {
    val psiManager = JavaPsiFacade.getInstance(getProject)
    val targetClass = psiManager.findClass(targetClassFqn, GlobalSearchScope.allScope(getProject))
    val actualSubtypeNames = directSubtypes(targetClass).map(_.getName)

    assertEquals(expectedSubtypeNames, actualSubtypeNames)
  }

  def testSubtypes_JavaBaseWithScalaTraitAndScalaClass(): Unit = {
    myFixture.addFileToProject(
      "JavaBase.java",
      //language=JAVA
      """public class JavaBase {
        |    public int foo() {
        |        return 0;
        |    }
        |}
        |""".stripMargin
    )
    myFixture.addFileToProject(
      "ScalaDefinitions.scala",
      //language=Scala
      """trait ScalaTrait extends JavaBase {
        |  override def foo = 1
        |}
        |
        |class ScalaClass extends ScalaTrait {
        |  override def foo = 2
        |}
        |""".stripMargin
    )

    assertDirectSubtypes("JavaBase", Seq("ScalaTrait"))
    assertDirectSubtypes("ScalaTrait", Seq("ScalaClass"))
  }

  def testDirectSupertypes_ClassExtendsClassAndTraits(): Unit = assertDirectSupertypes(
    s"""class Base
       |trait FirstTrait
       |trait SecondTrait
       |
       |class ${CARET}Child extends Base with FirstTrait with SecondTrait
       |""".stripMargin,
    Seq("Base", "FirstTrait", "SecondTrait")
  )

  def testDirectSupertypes_TraitExtendsMultipleTraits(): Unit = assertDirectSupertypes(
    s"""trait FirstParent
       |trait SecondParent
       |
       |trait ${CARET}Child extends FirstParent with SecondParent
       |""".stripMargin,
    Seq("FirstParent", "SecondParent", "java.lang.Object")
  )
}
