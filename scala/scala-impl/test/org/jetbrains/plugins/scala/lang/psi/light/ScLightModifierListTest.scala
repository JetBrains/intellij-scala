package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi.{CommonClassNames, PsiAnnotation, PsiAnnotationMemberValue, PsiArrayInitializerMemberValue, PsiMethod, PsiModifier, PsiModifierList, PsiParameter}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.OptionOpsForTest._
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScThisReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import org.junit.Assert.{assertEquals, assertFalse, assertNotNull, assertNull}

class ScLightModifierListTest extends ScalaLightCodeInsightFixtureTestCase {

  // SCL-25723
  def testGetAnnotation_WithBacktickedNamedArgument(): Unit = {
    myFixture.addClass(
      """public @interface BacktickedSchema {
        |  String type();
        |}
        |""".stripMargin
    )
    val file = configureScalaFromFileText(
      """import scala.annotation.meta.field
        |
        |case class Direct(
        |  @(BacktickedSchema @field)(`type` = "boolean")
        |  value: Boolean
        |)
        |""".stripMargin
    )

    val annotation = getAnnotationForParameter(file, "Direct", "value", "BacktickedSchema")
    assertAnnotationAttributeText(annotation, "type", "\"boolean\"")
  }

  def testGetAnnotation_PreservesBacktickedJavaCompatibleAttributeNames(): Unit = {
    myFixture.addClass(
      """public @interface JavaNameSchema {
        |  String type();
        |  String foo$plus();
        |}
        |""".stripMargin
    )
    val file = configureScalaFromFileText(
      """import scala.annotation.meta.field
        |
        |case class JavaNames(
        |  @(JavaNameSchema @field)(`type` = "escaped", `foo$plus` = "plain")
        |  flag: Boolean
        |)
        |""".stripMargin
    )

    val annotation = getAnnotationForParameter(file, "JavaNames", "flag", "JavaNameSchema")
    assertAnnotationAttributeText(annotation, "type", "\"escaped\"")
    assertAnnotationAttributeText(annotation, "foo$plus", "\"plain\"")
  }

  def testGetAnnotation_WithLiteralArguments(): Unit = {
    myFixture.addClass(
      """public @interface LiteralSchema {
        |  String type();
        |  int count();
        |  boolean enabled();
        |}
        |""".stripMargin
    )
    val file = configureScalaFromFileText(
      """import scala.annotation.meta.field
        |
        |case class Literals(
        |  @(LiteralSchema @field)(`type` = "text", count = 1, enabled = true)
        |  flag: Boolean
        |)
        |""".stripMargin
    )

    val annotation = getAnnotationForParameter(file, "Literals", "flag", "LiteralSchema")
    assertAnnotationAttributeText(annotation, "type", "\"text\"")
    assertAnnotationAttributeText(annotation, "count", "1")
    assertAnnotationAttributeText(annotation, "enabled", "true")
  }

  def testGetAnnotation_WithMultilineStringArgument(): Unit = {
    myFixture.addClass(
      """public @interface MultilineSchema {
        |  String type();
        |}
        |""".stripMargin
    )
    val multilineString = "\"\"\"line one\nline two\"\"\""
    val file = configureScalaFromFileText(
      s"""import scala.annotation.meta.field
         |
         |case class Multiline(
         |  @(MultilineSchema @field)(`type` = $multilineString)
         |  flag: Boolean
         |)
         |""".stripMargin
    )

    val annotation = getAnnotationForParameter(file, "Multiline", "flag", "MultilineSchema")
    assertAnnotationAttributeText(annotation, "type", "\"line one\\nline two\"")
  }

  def testGetAnnotation_WithArrayArgument(): Unit = {
    myFixture.addClass(
      """public @interface ArraySchema {
        |  String[] type();
        |}
        |""".stripMargin
    )
    val file = configureScalaFromFileText(
      """import scala.annotation.meta.field
        |
        |case class Arrays(
        |  @(ArraySchema @field)(`type` = Array("first", "second"))
        |  flag: Boolean
        |)
        |""".stripMargin
    )

    val annotation = getAnnotationForParameter(file, "Arrays", "flag", "ArraySchema")
    val value = annotationAttribute(annotation, "type").asInstanceOf[PsiArrayInitializerMemberValue]
    assertEquals(Seq("\"first\"", "\"second\""), value.getInitializers.toSeq.map(_.getText))
  }

  def testGetAnnotation_WithClassOfArgument(): Unit = {
    myFixture.addClass(
      """public @interface ClassSchema {
        |  Class<?> type();
        |}
        |""".stripMargin
    )
    val file = configureScalaFromFileText(
      """import scala.annotation.meta.field
        |
        |case class Classes(
        |  @(ClassSchema @field)(`type` = classOf[String])
        |  flag: Boolean
        |)
        |""".stripMargin
    )

    val annotation = getAnnotationForParameter(file, "Classes", "flag", "ClassSchema")
    assertAnnotationAttributeText(annotation, "type", "java.lang.String.class")
  }

  def testGetAnnotation_WithNestedAnnotationArgument(): Unit = {
    myFixture.addClass(
      """public @interface NestedInner {
        |  String type();
        |}
        |""".stripMargin
    )
    myFixture.addClass(
      """public @interface NestedOuter {
        |  NestedInner nested();
        |}
        |""".stripMargin
    )
    val file = configureScalaFromFileText(
      """import scala.annotation.meta.field
        |
        |case class Nested(
        |  @(NestedOuter @field)(nested = new NestedInner(`type` = "nested"))
        |  flag: Boolean
        |)
        |""".stripMargin
    )
    val outer = getAnnotationForParameter(file, "Nested", "flag", "NestedOuter")

    val inner = annotationAttribute(outer, "nested").asInstanceOf[PsiAnnotation]
    assertAnnotationAttributeText(inner, "type", "\"nested\"")
  }

  def testGetAnnotation_WithEmptyArguments(): Unit = {
    myFixture.addClass("public @interface Marker {}")
    myFixture.addClass("public @interface EmptyInner {}")
    myFixture.addClass("public @interface EmptyOuter { EmptyInner nested(); }")
    val file = configureScalaFromFileText(
      """import scala.annotation.meta.field
        |
        |case class EmptyArguments(
        |  @(Marker @field)
        |  @(EmptyOuter @field)(nested = new EmptyInner)
        |  value: Int
        |)
        |""".stripMargin
    )

    val marker = getAnnotationForParameter(file, "EmptyArguments", "value", "Marker")
    val outer = getAnnotationForParameter(file, "EmptyArguments", "value", "EmptyOuter")

    assertEquals(0, marker.getParameterList.getAttributes.length)
    assertEquals("@EmptyInner", annotationAttribute(outer, "nested").getText)
  }

  def testGetAnnotations_ExposesRegularAndOverrideAnnotations(): Unit = {
    myFixture.addClass("public @interface Kept {}")
    val file = configureScalaFromFileText(
      """trait Parent {
        |  def method(): Unit
        |}
        |
        |class Child extends Parent {
        |  @Kept override def method(): Unit = ()
        |}
        |""".stripMargin
    )

    val modifierList = getMethod(file, "Child", "method").getModifierList

    assertAnnotationNames(modifierList, Seq("Kept", CommonClassNames.JAVA_LANG_OVERRIDE))
    assertNotNull(modifierList.findAnnotation("Kept"))
    assertNotNull(modifierList.findAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE))
    assertNull(modifierList.findAnnotation("missing.Annotation"))
  }

  def testGetAnnotations_FiltersScalaSpecificAnnotations(): Unit = {
    myFixture.addClass("public @interface Kept {}")
    addScalaFileToProject(
      "KeywordFiltered.scala",
      """package `class`
        |
        |class KeywordFiltered extends scala.annotation.StaticAnnotation
        |""".stripMargin
    )
    val file = configureScalaFromFileText(
      """import scala.annotation.{inline, unchecked}
        |import scala.beans.BeanProperty
        |
        |class Filtered {
        |  @Kept def kept(): Unit = ()
        |  @inline def inlineMethod(): Unit = ()
        |  @throws[IllegalArgumentException]("reason") def throwsMethod(): Unit = ()
        |  @unchecked def uncheckedMethod(): Unit = ()
        |  @BeanProperty var bean: Int = 0
        |  @`class`.KeywordFiltered def keywordMethod(): Unit = ()
        |}
        |""".stripMargin
    )

    assertAnnotationNames(getMethod(file, "Filtered", "kept").getModifierList, Seq("Kept"))
    assertNoAnnotations(getMethod(file, "Filtered", "inlineMethod").getModifierList)
    assertNoAnnotations(getMethod(file, "Filtered", "throwsMethod").getModifierList)
    assertNoAnnotations(getMethod(file, "Filtered", "uncheckedMethod").getModifierList)
    assertNoAnnotations(getMethod(file, "Filtered", "bean").getModifierList)
    assertNoAnnotations(getMethod(file, "Filtered", "keywordMethod").getModifierList)
  }

  def testGetModifiers_MapsKeywordAnnotations(): Unit = {
    addScalaFileToProject(
      "ScalaJsNative.scala",
      """package scala.scalajs.js
        |
        |class native extends scala.annotation.StaticAnnotation
        |""".stripMargin
    )
    val file = configureScalaFromFileText(
      """abstract class KeywordModifiers(
        |  @scala.transient val transientValue: Int,
        |  @scala.volatile var volatileValue: Int
        |) {
        |  @scala.native def nativeMethod(): Unit
        |  @scala.annotation.strictfp def strictMethod(): Unit = ()
        |  @scala.scalajs.js.native def scalaJsNativeMethod(): Unit
        |}
        |""".stripMargin
    )

    val keywordModifiers = findClass(file, "KeywordModifiers")

    assertModifiers(ScLightModifierList(findScalaFunction(file, "KeywordModifiers", "nativeMethod")), PsiModifier.PUBLIC, PsiModifier.NATIVE)
    assertModifiers(ScLightModifierList(findScalaFunction(file, "KeywordModifiers", "strictMethod")), PsiModifier.PUBLIC, PsiModifier.STRICTFP)
    assertModifiers(ScLightModifierList(findScalaFunction(file, "KeywordModifiers", "scalaJsNativeMethod")), PsiModifier.PUBLIC, PsiModifier.NATIVE)
    assertNoAnnotations(ScLightModifierList(findScalaFunction(file, "KeywordModifiers", "nativeMethod")))
    assertModifiers(findConstructorParameter(keywordModifiers, "transientValue").getModifierList, PsiModifier.PUBLIC, PsiModifier.TRANSIENT)
    assertModifiers(findConstructorParameter(keywordModifiers, "volatileValue").getModifierList, PsiModifier.PUBLIC, PsiModifier.VOLATILE)
  }

  def testGetModifiers_HandlesAccessFinalAndAbstractMembers(): Unit = {
    val file = configureScalaFromFileText(
      """package modifiers
        |
        |class AccessModifiers(
        |  val publicParameter: Int,
        |  private val privateParameter: Int
        |) {
        |  def publicMethod(): Unit = ()
        |  private def privateMethod(): Unit = ()
        |  private[this] def privateThisMethod(): Unit = ()
        |  private[modifiers] def qualifiedPrivateMethod(): Unit = ()
        |  final def finalClassMethod(): Unit = ()
        |  def parameterMethod(parameter: Int): Unit = ()
        |}
        |
        |trait TraitModifiers {
        |  final def finalTraitMethod(): Unit = ()
        |}
        |
        |abstract class AbstractModifiers {
        |  def abstractMethod(): Unit
        |}
        |""".stripMargin
    )

    val accessModifiers = findClass(file, "AccessModifiers")

    assertModifiers(getMethod(file, "AccessModifiers", "publicMethod").getModifierList, PsiModifier.PUBLIC)
    assertModifiers(getMethod(file, "AccessModifiers", "privateMethod").getModifierList, PsiModifier.PRIVATE)
    assertModifiers(ScLightModifierList(findScalaFunction(file, "AccessModifiers", "privateThisMethod")), PsiModifier.PRIVATE)
    assertModifiers(ScLightModifierList(findScalaFunction(file, "AccessModifiers", "qualifiedPrivateMethod")), PsiModifier.PUBLIC)
    assertModifiers(getMethod(file, "AccessModifiers", "finalClassMethod").getModifierList, PsiModifier.PUBLIC, PsiModifier.FINAL)
    assertModifiers(getMethod(file, "TraitModifiers", "finalTraitMethod").getModifierList, PsiModifier.PUBLIC)
    assertModifiers(getMethod(file, "AbstractModifiers", "abstractMethod").getModifierList, PsiModifier.PUBLIC, PsiModifier.ABSTRACT)
    assertModifiers(findConstructorParameter(accessModifiers, "publicParameter").getModifierList, PsiModifier.PUBLIC)
    assertModifiers(findConstructorParameter(accessModifiers, "privateParameter").getModifierList, PsiModifier.PRIVATE)

    val methodParameter = getMethod(file, "AccessModifiers", "parameterMethod").getParameterList.getParameters.head
    assertModifiers(methodParameter.getModifierList)
  }

  def testGetModifiers_UsesExplicitFlagsAndEmptyFactory(): Unit = {
    val file = configureScalaFromFileText(
      """class ExplicitFlags {
        |  def method(): Unit = ()
        |}
        |""".stripMargin
    )

    val function = findScalaFunction(file, "ExplicitFlags", "method")
    val modifiers = ScLightModifierList(function, isStatic = true, isAbstract = true, isOverride = true)

    assertModifiers(modifiers, PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.ABSTRACT)
    assertNotNull(modifiers.findAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE))

    val empty = ScLightModifierList.empty(function.getManager)
    assertModifiers(empty)
    assertNoAnnotations(empty)
  }

  def testGetModifiers_UsesBindingPatternOwner(): Unit = {
    myFixture.addClass("public @interface Kept {}")
    val file = configureScalaFromFileText(
      """class BindingOwner {
        |  @Kept val binding: Int = 0
        |}
        |""".stripMargin
    )

    val modifierList = getMethod(file, "BindingOwner", "binding").getModifierList

    assertAnnotationNames(modifierList, Seq("Kept"))
    assertModifiers(modifierList, PsiModifier.PUBLIC)
  }

  def testGetModifiers_ReturnsEmptyForNonOwnerExpression(): Unit = {
    val file = configureScalaFromFileText(
      """class NoOwner {
        |  val self = this
        |}
        |""".stripMargin
    )

    val thisReference = Option(PsiTreeUtil.findChildOfType(file, classOf[ScThisReference]))
      .getOrFail("Can't find `this` reference")
    val modifierList = ScLightModifierList(thisReference)

    assertModifiers(modifierList)
    assertNoAnnotations(modifierList)
    assertFalse(modifierList.hasModifierProperty(PsiModifier.PUBLIC))
  }

  private def assertAnnotationAttributeText(annotation: PsiAnnotation, name: String, expectedText: String): Unit =
    assertEquals(expectedText, annotationAttribute(annotation, name).getText)

  private def annotationAttribute(annotation: PsiAnnotation, name: String): PsiAnnotationMemberValue = {
    val value = annotation.findDeclaredAttributeValue(name)
    assertNotNull(s"Java light PSI must expose the `$name` annotation attribute", value)
    value
  }

  private def getAnnotationForParameter(
    scalaFile: ScalaFile,
    className: String,
    parameterName: String,
    annotationName: String
  ): PsiAnnotation = {
    val caseClass = findClass(scalaFile, className)
    val parameter = findConstructorParameter(caseClass, parameterName)
    val annotation = parameter.getAnnotation(annotationName)
    assertNotNull(s"Java light PSI must expose the @$annotationName annotation", annotation)
    annotation
  }

  private def findClass(scalaFile: ScalaFile, className: String): ScClass = {
    val classes = scalaFile.typeDefinitions.filterByType[ScClass]
    val scalaClass = classes.find(_.name == className)
    scalaClass.getOrFail(s"Can't find class `$className`")
  }

  private def findTypeDefinition(scalaFile: ScalaFile, className: String): ScTypeDefinition = {
    val typeDefinitions = scalaFile.typeDefinitions
    val typeDefinition = typeDefinitions.find(_.name == className)
    typeDefinition.getOrFail(s"Can't find type definition `$className`")
  }

  private def findConstructorParameter(caseClass: ScClass, parameterName: String): PsiParameter = {
    val constructors = caseClass.getConstructors
    val constructorIterator = constructors.iterator
    val parameterLists = constructorIterator.map(_.getParameterList)
    val parameters = parameterLists.flatMap(_.getParameters.iterator)
    val parameter = parameters.find(_.name == parameterName)
    parameter.getOrFail(s"Can't find constructor parameter `$parameterName`")
  }

  private def getMethod(scalaFile: ScalaFile, className: String, methodName: String): PsiMethod = {
    val typeDefinition = findTypeDefinition(scalaFile, className)
    val methods = typeDefinition.getMethods
    val method = methods.find(_.name == methodName)
    method.getOrFail(s"Can't find Java-facing method `$methodName` in `$className`")
  }

  private def findScalaFunction(scalaFile: ScalaFile, className: String, methodName: String): ScFunction = {
    val typeDefinition = findTypeDefinition(scalaFile, className)
    val members = typeDefinition.members
    val functions = members.filterByType[ScFunction]
    val function = functions.find(_.name == methodName)
    function.getOrFail(s"Can't find Scala function `$methodName` in `$className`")
  }

  private def assertAnnotationNames(modifierList: PsiModifierList, expected: Seq[String]): Unit = {
    val annotations = modifierList.getAnnotations
    val names = annotations.toSeq.map(_.getQualifiedName)
    assertEquals(expected, names)
  }

  private def assertNoAnnotations(modifierList: PsiModifierList): Unit =
    assertAnnotationNames(modifierList, Seq.empty)

  private def assertModifiers(modifierList: PsiModifierList, expected: String*): Unit = {
    relevantModifiers.foreach { modifier =>
      val expectedValue = expected.contains(modifier)
      assertEquals(s"hasModifierProperty($modifier)", expectedValue, modifierList.hasModifierProperty(modifier))
      assertEquals(s"hasExplicitModifier($modifier)", expectedValue, modifierList.hasExplicitModifier(modifier))
    }
  }

  private val relevantModifiers = Seq(
    PsiModifier.PUBLIC,
    PsiModifier.PRIVATE,
    PsiModifier.STATIC,
    PsiModifier.ABSTRACT,
    PsiModifier.FINAL,
    PsiModifier.NATIVE,
    PsiModifier.STRICTFP,
    PsiModifier.VOLATILE,
    PsiModifier.TRANSIENT
  )
}
