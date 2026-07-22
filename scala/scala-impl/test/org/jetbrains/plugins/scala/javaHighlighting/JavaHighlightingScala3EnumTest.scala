package org.jetbrains.plugins.scala.javaHighlighting

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{JavaPsiFacade, PsiClass, PsiEnumConstant, PsiModifier}
import com.intellij.testFramework.UsefulTestCase.assertInstanceOf
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumSingletonCase
import org.junit.Assert.{assertEquals, assertFalse, assertNotNull, assertTrue, fail}

//noinspection ScalaWrongPlatformMethodsUsage
class JavaHighlightingScala3EnumTest extends JavaHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0

  def testAllEnumCasesSingleton_EnumCompanionMethodsResolveFromJava(): Unit = {
    assertNoErrorsInJava(
      """enum TestEnum:
        |  case One, Two
        |""".stripMargin,
      """public class JavaUsage {
        |    TestEnum[] all = TestEnum.values();
        |    TestEnum byName = TestEnum.valueOf("One");
        |    TestEnum byOrdinal = TestEnum.fromOrdinal(0);
        |}
        |""".stripMargin, "JavaUsage"
    )
  }

  def testMixedEnumCases_ExposesOnlyFromOrdinalToJava(): Unit = {
    assertErrorsTextInJava(
      """enum TestEnum:
        |  case Singleton
        |  case Parameterized(value: Int)
        |""".stripMargin,
      """public class JavaUsage {
        |    TestEnum[] all = TestEnum.values();
        |    TestEnum byName = TestEnum.valueOf("One");
        |    TestEnum byOrdinal = TestEnum.fromOrdinal(0);
        |}
        |""".stripMargin,
      "JavaUsage",
      """Error(values,Cannot resolve method 'values' in 'TestEnum')
        |Error(valueOf,Cannot resolve method 'valueOf' in 'TestEnum')""".stripMargin
    )

    val enumClass = findPsiClass("TestEnum")
    assertSingleMethodCanBeFoundInClass(enumClass, "fromOrdinal")
    assertNoMethodFoundInClass(enumClass, "values")
    assertNoMethodFoundInClass(enumClass, "valueOf")
  }


  private def assertSingleMethodCanBeFoundInClass(psiClass: PsiClass, methodName: String): Unit = {
    val methods = psiClass.findMethodsByName(methodName, false)
    if (methods.isEmpty)
      fail(s"Method $methodName not found in class ${psiClass.getName}")
    else if (methods.size > 1) {
      fail(s"Method $methodName found multiple times in class ${psiClass.getName}")
    }
  }

  private def assertNoMethodFoundInClass(psiClass: PsiClass, methodName: String): Unit = {
    val methods = psiClass.findMethodsByName(methodName, false)
    if (methods.nonEmpty) {
      fail(s"Method $methodName should not be found in class ${psiClass.getName}")
    }
  }

  def testJavaCompatibleEnumConstantsAndSwitchLabels(): Unit = {
    assertNoErrorsInJava(
      """enum TestEnum extends java.lang.Enum[TestEnum]:
        |  case One, Two
        |""".stripMargin,
      """public class JavaUsage {
        |    TestEnum one = TestEnum.One;
        |    TestEnum two = TestEnum.Two;
        |
        |    int number(TestEnum value) {
        |        switch (value) {
        |            case One: return 1;
        |            case Two: return 2;
        |            default: throw new IllegalArgumentException();
        |        }
        |    }
        |}
        |""".stripMargin, "JavaUsage"
    )

    val enumClass = findPsiClass("TestEnum")
    assertTrue("Java view of Scala java-compatible enum class should be a Java enum as well", enumClass.isEnum)

    assertEnumConstantProperties(enumClass, "One")
    assertEnumConstantProperties(enumClass, "Two")
  }

  private def assertEnumConstantProperties(enumClass: PsiClass, name: String): Unit = {
    val enumConstant = assertInstanceOf(enumClass.findFieldByName(name, false), classOf[PsiEnumConstant])
    assertEquals("TestEnum", enumConstant.getType.getCanonicalText)
    assertTrue("Scala java-compatible enum Java view should be public", enumConstant.hasModifierProperty(PsiModifier.PUBLIC))
    assertTrue("Scala java-compatible enum Java view should be static", enumConstant.hasModifierProperty(PsiModifier.STATIC))
    assertTrue("Scala java-compatible enum Java view should be final", enumConstant.hasModifierProperty(PsiModifier.FINAL))

    val navigationElement = assertInstanceOf(enumConstant.getNavigationElement, classOf[ScEnumSingletonCase])
    assertInstanceOf(navigationElement, classOf[ScEnumSingletonCase])
    assertEquals(name, navigationElement.name)
  }

  def testOrdinaryScalaEnumIsNotJavaEnumAndHasNoCaseFields(): Unit = {
    myFixture.addFileToProject(
      "OrdinaryEnum.scala",
      //language=Scala 3
      """enum OrdinaryEnum:
        |  case One, Two
        |""".stripMargin
    )

    val enumClass = findPsiClass("OrdinaryEnum")
    assertFalse("Java view of Scala NON-java-compatible enum class should not be a Java enum", enumClass.isEnum)
    val classFieldNames = enumClass.getFields.iterator.map(_.getName)
    val matchingClassFieldNames = classFieldNames.filter(Set("One", "Two")).toSeq
    assertEquals("Java view of Scala NON-java-compatible enum class should not contain fields with Scala enum cases names", Seq.empty, matchingClassFieldNames)
  }

  def testJavaCompatibleEnumParentMustUseSelfType(): Unit = {
    myFixture.addFileToProject(
      "WrongJavaEnum.scala",
      // YES: this is a compilation error. IntelliJ will show: "Type Other does not conform to upper bound Enum[Other] of type parameter E"
      //language=Scala 3
      """final class Other
        |
        |enum WrongJavaEnum extends java.lang.Enum[Other]:
        |  case Value
        |""".stripMargin
    )

    val enumClass = findPsiClass("WrongJavaEnum")
    assertFalse("Java view of an invalid Scala java-compatible enum class should not be a Java enum", enumClass.isEnum)
    assertEquals(0, enumClass.getFields.count(_.getName == "Value"))
  }

  private def findPsiClass(qualifiedName: String): PsiClass = {
    val result = JavaPsiFacade.getInstance(getProject).findClass(
      qualifiedName,
      GlobalSearchScope.projectScope(getProject)
    )
    assertNotNull(s"Cannot find class $qualifiedName", result)
    result
  }
}
