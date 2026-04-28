package org.jetbrains.plugins.scala.lang.psi

import org.jetbrains.plugins.scala.OptionOpsForTest._
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScParameterOwner}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.junit.Assert.{assertNull, assertTrue}

import scala.collection.immutable.ListSet

class ScalaPsiUtilTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  def testIsInsideImportExpression(): Unit = {
    val textWithImports =
      """import scala.util.Random
        |import scala.util.{
        |   Random,
        |   ChainingOps => ChainingOpsRenamed,
        |   Either => _,
        |   _
        |}
        |""".stripMargin

    getFixture.configureByText("a.scala", textWithImports)

    val importExpressions = getFile.elements.filterByType[ScImportExpr].toSeq
    val elementsInsideImports = importExpressions.flatMap(_.depthFirst()).to(ListSet) -- importExpressions
    elementsInsideImports.foreach { child =>
      assertTrue(
        s"Element at range ${child.getTextRange} with text `${child.getText}` is in import expression but isInsideImportExpression returned false",
        ScalaPsiUtil.isInsideImportExpression(child)
      )
    }
  }

  // Direct branch-level test coverage for:
  // `org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.parameterForSyntheticParameter`.
  //
  // Complementary indirect end-to-end coverage exists in:
  // `org.jetbrains.plugins.scala.intelliLang.injection.ScalaLanguageInjectorTest`
  // (for SCL-24959).
  //
  // Play-template-specific direct coverage exists in:
  // `com.intellij.scala.play.lang.resolve.PlayTemplateSyntheticParameterMappingTest`.
  def testParameterForSyntheticParameter_NonSyntheticOwner(): Unit = {
    configureScalaFromFileText(
      """object Usage {
        |  def plain(sql: String): Unit = ()
        |}
        |""".stripMargin
    )

    val function = PsiFinder.findFunction("plain")
    val parameter = PsiFinder.findParameter(function, "sql")

    assertParameterForSyntheticParameter_IsNotFound(parameter, "the owner is not synthetic")
  }

  def testParameterForSyntheticParameter_CaseClassApply_MapsToConstructorParameter_WithDefaultSyntheticMetadata(): Unit = {
    configureScalaFromFileText(
      """case class MyCaseClass(sql: String)
        |""".stripMargin
    )

    val caseClass = PsiFinder.findClass("MyCaseClass")
    val applyMethod = PsiFinder.findSyntheticApply(caseClass)

    val syntheticParameter = PsiFinder.findParameter(applyMethod, "sql")
    val constructorParameter = PsiFinder.findConstructorParameter(caseClass, "sql")

    assertParameterForSyntheticParameter_ResolvesToParameter(
      syntheticParameter,
      constructorParameter,
      "Expected case class synthetic `apply` parameter to map to constructor parameter using default synthetic metadata"
    )
  }

  def testParameterForSyntheticParameter_UsesSyntheticNavigationElement_WhenItIsParameterOwner(): Unit = {
    configureScalaFromFileText(
      """object Scope {
        |  implicit class SqlOps(sql: String)
        |}
        |""".stripMargin
    )

    val implicitClass = PsiFinder.findClass("SqlOps")
    val constructorParameter = PsiFinder.findConstructorParameter(implicitClass, "sql")
    val (_, syntheticParameter) = PsiFinder.findSyntheticImplicitParameter("SqlOps", "sql")

    assertParameterForSyntheticParameter_ResolvesToParameter(
      syntheticParameter,
      constructorParameter,
      "Expected synthetic navigation element fallback to use `ScParameterOwner` directly"
    )
  }

  def testParameterForSyntheticParameter_UsesSyntheticNavigationTypeDefinitionCompanionFallback(): Unit = {
    configureScalaFromFileText(
      """class Target(sql: String)
        |object Target
        |
        |object Scope {
        |  implicit class SqlOps(sql: String)
        |}
        |""".stripMargin
    )

    val targetClass = PsiFinder.findClass("Target")
    val targetObject = PsiFinder.findObject("Target")
    val targetConstructorParameter = PsiFinder.findConstructorParameter(targetClass, "sql")
    val (syntheticMethod, syntheticParameter) = PsiFinder.findSyntheticImplicitParameter("SqlOps", "sql")

    syntheticMethod.syntheticNavigationElement = targetObject

    assertParameterForSyntheticParameter_ResolvesToParameter(
      syntheticParameter,
      targetConstructorParameter,
      "Expected `ScTypeDefinition` synthetic navigation fallback (object) to resolve via companion class constructor"
    )
  }

  def testParameterForSyntheticParameter_ReturnsNone_WhenSyntheticNavigationElementIsUnsupported(): Unit = {
    configureScalaFromFileText(
      """object Scope {
        |  implicit class SqlOps(sql: String)
        |}
        |""".stripMargin
    )

    val (syntheticMethod, syntheticParameter) = PsiFinder.findSyntheticImplicitParameter("SqlOps", "sql")

    syntheticMethod.syntheticNavigationElement = getFile

    assertParameterForSyntheticParameter_IsNotFound(
      syntheticParameter,
      "syntheticNavigationElement is `PsiFile`, while `originalParametersOwnerForSyntheticNavigationElement` handles only `ScTypeDefinition`/`ScParameterOwner`"
    )
  }

  private def assertParameterForSyntheticParameter_IsNotFound(
    parameter: ScParameter,
    reason: String
  ): Unit = {
    val resolved = ScalaPsiUtil.parameterForSyntheticParameter(parameter)
    assertTrue(
      s"Expected no synthetic parameter mapping for `${parameter.name}` because $reason; resolved = ${renderParameter(resolved)}",
      resolved.isEmpty
    )
  }

  private def assertParameterForSyntheticParameter_ResolvesToParameter(
    syntheticParameter: ScParameter,
    expectedParameter: ScParameter,
    because: String
  ): Unit = {
    val resolved = ScalaPsiUtil.parameterForSyntheticParameter(syntheticParameter)
    assertTrue(
      s"$because; resolved = ${renderParameter(resolved)}",
      resolved.contains(expectedParameter)
    )
  }

  private def renderParameter(parameter: Option[ScParameter]): String =
    parameter match {
      case Some(param) => s"Some(${param.name}, owner = ${param.owner})"
      case None => "None"
    }

  private object PsiFinder {
    private def assertOriginalParametersOwnerIsNull(function: ScFunction, foundBy: String): Unit = {
      assertNull(
        s"Expected `originalParametersOwner` to be null for function `${function.name}` " +
          s"resolved by $foundBy in `${classOf[ScalaPsiUtilTest].getName}`; " +
          s"non-null values are mainly expected for Play template synthetic methods; " +
          s"actual owner = ${function.originalParametersOwner}",
        function.originalParametersOwner
      )
    }

    def findClass(name: String): ScClass =
      getFile.breadthFirst()
        .collectFirst { case clazz: ScClass if clazz.name == name && !clazz.isSynthetic => clazz }
        .getOrFail(s"Can't find class `$name`")

    def findObject(name: String): ScObject =
      getFile.breadthFirst()
        .collectFirst { case obj: ScObject if obj.name == name && !obj.isSyntheticObject => obj }
        .getOrFail(s"Can't find object `$name`")

    def findFunction(name: String): ScFunction = {
      val result = getFile.depthFirst()
        .collectFirst { case function: ScFunction if function.name == name && !function.isSynthetic => function }
        .getOrFail(s"Can't find function `$name`")
      assertOriginalParametersOwnerIsNull(result, s"`findFunction($name)`")
      result
    }

    def findConstructorParameter(clazz: ScClass, name: String): ScParameter =
      clazz.constructor.toSeq
        .flatMap(_.parameters)
        .find(_.name == name)
        .getOrFail(s"Can't find constructor parameter `$name` for class `${clazz.name}`")

    def findParameter(owner: ScParameterOwner, name: String): ScParameter =
      owner.parameters.find(_.name == name)
        .getOrFail(s"Can't find parameter `$name`; actual parameters: ${owner.parameters.map(_.name)}, owner = $owner")

    def findSyntheticApply(caseClass: ScClass): ScFunction = {
      val companion = ScalaPsiUtil.getCompanionModule(caseClass).getOrFail(s"Can't find companion for `${caseClass.name}`")
      val result = (companion.syntheticMethods ++ companion.functions)
        .find(f => f.isSynthetic && f.isApplyMethod)
        .getOrFail(s"Can't find synthetic apply method for `${caseClass.name}`")
      assertOriginalParametersOwnerIsNull(result, s"`findSyntheticApply(${caseClass.name})`")
      result
    }

    def findSyntheticImplicitParameter(className: String, parameterName: String ): (ScFunction, ScParameter) = {
      val implicitClass = findClass(className)
      val syntheticImplicitMethod = implicitClass.getSyntheticImplicitMethod
        .getOrFail(s"Can't find synthetic implicit conversion method for `$className`")
      assertOriginalParametersOwnerIsNull(
        syntheticImplicitMethod,
        s"`findSyntheticImplicitParameter($className, $parameterName)`"
      )
      val syntheticParameter = findParameter(syntheticImplicitMethod, parameterName)
      (syntheticImplicitMethod, syntheticParameter)
    }
  }
}
