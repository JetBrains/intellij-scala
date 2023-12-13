package org.jetbrains.plugins.scala.uast

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiType}
import junit.framework.TestCase
import junit.framework.TestCase.{assertEquals, assertNotNull, assertNull, assertTrue}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{ObjectExt, StringExt, inWriteCommandAction}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariableDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createScalaElementFromTextWithContext, createScalaFileFromText}
import org.jetbrains.plugins.scala.lang.psi.types.api.PsiTypeConstants
import org.jetbrains.plugins.scala.lang.psi.uast.utils.NotNothing
import org.jetbrains.plugins.scala.project.{ProjectContext, ScalaFeatures}
import org.jetbrains.plugins.scala.uast.AbstractUastFixtureTest.findUElementByTextFromPsi
import org.jetbrains.plugins.scala.uast.ScalaUastGenerationTest.UElementExt
import org.jetbrains.uast.generate.{UParameterInfo, UastCodeGenerationPlugin, UastElementFactory}

import java.{util => ju}
import org.jetbrains.uast._

import scala.reflect.{ClassTag, classTag}

class ScalaUastGenerationTest extends ScalaLightCodeInsightFixtureTestCase {

  private lazy val generatePlugin: UastCodeGenerationPlugin =
    UastCodeGenerationPlugin.byLanguage(ScalaLanguage.INSTANCE)

  private lazy val uastElementFactory: UastElementFactory =
    generatePlugin.getElementFactory(myFixture.getProject)

  def testBinaryExpressionWithSimpleOperands(): Unit = {
    val left = uastExpressionFromText("true")
    val right = uastExpressionFromText("false")

    val expression = uastElementFactory.createBinaryExpression(left, right, UastBinaryOperator.LOGICAL_AND, null)

    assertEquals("true && false", expression.getSourcePsi.getText)
  }

  def testBinaryExpressionWithSimpleOperandsInParenthesis(): Unit = {
    val left = uastExpressionFromText("(true)")
    val right = uastExpressionFromText("(false)")

    val expression = uastElementFactory.createFlatBinaryExpression(left, right, UastBinaryOperator.LOGICAL_AND, null)

    TestCase.assertTrue(expression.getSourcePsi.is[ScInfixExpr])
    assertEquals("true && false", expression.getSourcePsi.getText)
    assertEquals(
      """
        |UBinaryExpression (operator = &&)
        |    ULiteralExpression (value = true)
        |    ULiteralExpression (value = false)
        |""".stripMargin.withNormalizedSeparator.trim,
      UastUtils.asRecursiveLogString(expression).withNormalizedSeparator.trim)
  }

  def testBinaryExpressionWithSimpleOperandsInParenthesisPolyadic(): Unit = {
    val left = uastExpressionFromText("(true && false)")
    val right = uastExpressionFromText("(false)")

    val expression = uastElementFactory.createFlatBinaryExpression(left, right, UastBinaryOperator.LOGICAL_AND, null)

    TestCase.assertTrue(expression.getSourcePsi.is[ScInfixExpr])
    assertEquals("true && false && false", expression.getSourcePsi.getText)
    assertEquals(
      """
        |UBinaryExpression (operator = &&)
        |    UBinaryExpression (operator = &&)
        |        ULiteralExpression (value = true)
        |        ULiteralExpression (value = false)
        |    ULiteralExpression (value = false)
        |""".stripMargin.withNormalizedSeparator.trim,
      UastUtils.asRecursiveLogString(expression).withNormalizedSeparator.trim)
  }

  def testBinaryExpressionWithSimpleOperandsOfDifferencePrecedenceInParenthesisPolyadic(): Unit = {
    val left = uastExpressionFromText("(true || false)")
    val right = uastExpressionFromText("(false)")

    val expression = uastElementFactory.createFlatBinaryExpression(left, right, UastBinaryOperator.LOGICAL_AND, null)

    TestCase.assertTrue(expression.getSourcePsi.is[ScInfixExpr])
    assertEquals("(true || false) && false", expression.getSourcePsi.getText)
    assertEquals(
      """
        |UBinaryExpression (operator = &&)
        |    UParenthesizedExpression
        |        UBinaryExpression (operator = ||)
        |            ULiteralExpression (value = true)
        |            ULiteralExpression (value = false)
        |    ULiteralExpression (value = false)
        |""".stripMargin.withNormalizedSeparator.trim,
      UastUtils.asRecursiveLogString(expression).withNormalizedSeparator.trim)
  }

  def testEmptyBlockExpression(): Unit = {
    val block = uastElementFactory.createBlockExpression(javaList(), null)

    assertEquals(
      """
        |{
        |
        |}
        |""".stripMargin.withNormalizedSeparator.trim,
      block.getSourcePsi.getText)
  }

  def testBlockExpression(): Unit = {
    val statement1 = uastExpressionFromText("println()")
    val statement2 = uastExpressionFromText("println(2)")
    val block = uastElementFactory.createBlockExpression(javaList(statement1, statement2), null)

    assertEquals(
      """
        |{
        |println()
        |println(2)
        |}
        |""".stripMargin.withNormalizedSeparator.trim,
      block.getSourcePsi.getText)
  }

  def testCallExpressionWithReceiver(): Unit = {
    val receiver = uastExpressionFromText(""""10"""")
    val arg1 = uastExpressionFromText("1")
    val arg2 = uastExpressionFromText("2")
    val methodCall = uastElementFactory.createCallExpression(
      receiver,
      "substring",
      javaList(arg1, arg2),
      null,
      UastCallKind.METHOD_CALL,
      null
    )

    assertEquals(""""10".substring(1, 2)""", methodCall.getSourcePsi.getText)
  }

  def testCallExpressionWithoutReceiver(): Unit = {
    val arg1 = uastExpressionFromText("1")
    val arg2 = uastExpressionFromText("2")
    val methodCall = uastElementFactory.createCallExpression(
      null,
      "substring",
      javaList(arg1, arg2),
      null,
      UastCallKind.METHOD_CALL,
      null
    )

    assertEquals("substring(1, 2)", methodCall.getSourcePsi.getText)
  }

  // TODO: Implement type arguments support in createCallExpression
  //  def testCallExpressionWithGenericsRestoring(): Unit = {
  //    val arrays = uastExpressionFromText("java.util.Arrays")
  //    val methodCall = uastElementFactory.createCallExpression(
  //      arrays,
  //      "asList",
  //      javaList(),
  //      psiTypeFromJavaTypeText("java.util.List<java.lang.String>"),
  //      UastCallKind.METHOD_CALL,
  //      null
  //    )
  //
  //    assertEquals("java.util.Arrays.asList[String]()", methodCall.getSourcePsi.getText)
  //  }
  //
  //  def testCallExpressionWithGenericsRestoringScala(): Unit = {
  //    val arrays = uastExpressionFromText("List")
  //    val methodCall = uastElementFactory.createCallExpression(
  //      arrays,
  //      "empty",
  //      javaList(),
  //      psiTypeFromJavaTypeText("scala.collection.immutable.List<scala.Int>"),
  //      UastCallKind.METHOD_CALL,
  //      null
  //    )
  //
  //    assertEquals("List.empty[Int]", methodCall.getSourcePsi.getText)
  //  }
  //
  //  def testCallExpressionWithGenericsRestoring2Params(): Unit = {
  //    val collections = uastExpressionFromText("java.util.Collections")
  //    val methodCall = uastElementFactory.createCallExpression(
  //      collections,
  //      "emptyMap",
  //      javaList(),
  //      psiTypeFromJavaTypeText("java.util.Map<java.lang.String, java.lang.Integer>"),
  //      UastCallKind.METHOD_CALL,
  //      null
  //    )
  //
  //    assertEquals("java.util.Collections.emptyMap[String, Int]()", methodCall.getSourcePsi.getText)
  //  }
  //
  //  def testCallExpressionWithGenericsRestoring2ParamsScala(): Unit = {
  //    val collections = uastExpressionFromText("Map")
  //    val methodCall = uastElementFactory.createCallExpression(
  //      collections,
  //      "empty",
  //      javaList(),
  //      psiTypeFromJavaTypeText("scala.collection.immutable.Map<java.lang.String, scala.Int>"),
  //      UastCallKind.METHOD_CALL,
  //      null
  //    )
  //
  //    assertEquals("Map.empty[String, Int]", methodCall.getSourcePsi.getText)
  //  }
  //
  //  def testCallExpressionWithGenericsRestoring1ParamWith1Existing(): Unit = {
  //    myFixture.addClass(
  //      """
  //        |class A {
  //        |  public static <T1, T2> java.util.Map<T1, T2> foo(T1 a) {
  //        |    return null;
  //        |  }
  //        |}
  //        |""".stripMargin.withNormalizedSeparator.trim
  //    )
  //
  //    val a = uastExpressionFromText("A")
  //    val param = uastElementFactory.createStringLiteralExpression("a", null)
  //    val methodCall = uastElementFactory.createCallExpression(
  //      a,
  //      "foo",
  //      javaList(param),
  //      psiTypeFromJavaTypeText("java.util.Map<java.lang.String, java.lang.Integer>"),
  //      UastCallKind.METHOD_CALL,
  //      null
  //    )
  //
  //    assertEquals("""A.foo[String, Int]("a")""", methodCall.getSourcePsi.getText)
  //  }
  //
  //  def testCallExpressionWithGenericsRestoring1ParamWith1Unused(): Unit = {
  //    myFixture.addClass(
  //      """
  //        |class A {
  //        |  public static <T1, T2, T3> java.util.Map<T1, T3> foo(T1 a) {
  //        |    return null;
  //        |  }
  //        |}
  //        |""".stripMargin.withNormalizedSeparator.trim
  //    )
  //
  //    val a = uastExpressionFromText("A")
  //    val param = uastElementFactory.createStringLiteralExpression("a", null)
  //    val methodCall = uastElementFactory.createCallExpression(
  //      a,
  //      "foo",
  //      javaList(param),
  //      psiTypeFromJavaTypeText("java.util.Map<java.lang.String, java.lang.Integer>"),
  //      UastCallKind.METHOD_CALL,
  //      null
  //    )
  //
  //    assertEquals("""A.foo[String, Any, Int]("a")""", methodCall.getSourcePsi.getText)
  //  }

  def testCallableReferenceWithReceiver(): Unit = {
    val receiver = uastExpressionFromText("scala.Predef")
    val callableRef = uastElementFactory.createCallableReferenceExpression(receiver, "println", null)
    assertEquals("scala.Predef.println", callableRef.getSourcePsi.getText)
  }

  def testCallableReferenceWithoutReceiver(): Unit = {
    val callableRef = uastElementFactory.createCallableReferenceExpression(null, "println", null)
    assertEquals("println", callableRef.getSourcePsi.getText)
  }

  def testIfExpression(): Unit = {
    val condition = uastExpressionFromText("true")
    val thenBranch = uastExpressionFromText("{ a(b); println() }")
    val ifExpr = uastElementFactory.createIfExpression(condition, thenBranch, null, null)
    assertEquals("if (true) { a(b); println() }", ifExpr.getSourcePsi.getText)
  }

  def testIfElseExpression(): Unit = {
    val condition = uastExpressionFromText("true")
    val thenBranch = uastExpressionFromText("{ a(b); println() }")
    val elseBranch = uastExpressionFromText("c += 1")
    val ifExpr = uastElementFactory.createIfExpression(condition, thenBranch, elseBranch, null)
    assertEquals("if (true) { a(b); println() } else c += 1", ifExpr.getSourcePsi.getText)
  }

  def testLambdaExpression(): Unit = {
    val params = javaList(
      new UParameterInfo(PsiTypeConstants.Int, "a"),
      new UParameterInfo(null, "b"),
    )
    val body = uastExpressionFromText("println()")

    val lambda = uastElementFactory.createLambdaExpression(params, body, null)
    assertEquals("(a: Int, b) => println()", lambda.getSourcePsi.getText)
  }

  def testLambdaExpressionWithoutParams(): Unit = {
    val lambda = uastElementFactory
      .createLambdaExpression(javaList(), uastExpressionFromText("println()"), null)

    assertEquals("() => println()", lambda.getSourcePsi.getText)
  }

  def testLambdaExpressionWithOneParamWithType(): Unit = {
    val params = javaList(new UParameterInfo(PsiTypeConstants.Int, null))
    val body = uastExpressionFromText("println()")
    val lambda = uastElementFactory.createLambdaExpression(params, body, null)

    assertEquals("(i: Int) => println()", lambda.getSourcePsi.getText)
  }

  def testLambdaExpressionWithOneParamWithoutType(): Unit = {
    val params = javaList(new UParameterInfo(null, null))
    val body = uastExpressionFromText("println()")
    val lambda = uastElementFactory.createLambdaExpression(params, body, null)

    assertEquals("value => println()", lambda.getSourcePsi.getText)
  }

  def testLambdaExpressionWithSimplifiedBlockReturnBody(): Unit = {
    val body = uastExpressionFromText("""{ return "10" }""")
    val lambda = uastElementFactory.createLambdaExpression(javaList(), body, null)

    assertEquals("""() => "10"""", lambda.getSourcePsi.getText)
  }

  def testLambdaExpressionWithBlockBody(): Unit = {
    val params = javaList(
      new UParameterInfo(PsiTypeConstants.Int, "a"),
      new UParameterInfo(PsiTypeConstants.Double, "b"),
    )
    val body = uastExpressionFromText("""{ println(a); println(b); return "10" }""")
    val lambda = uastElementFactory.createLambdaExpression(params, body, null)

    assertEquals(
      """
        |(a: Int, b: Double) => {
        |println(a)
        |println(b)
        |"10"
        |}
        |""".stripMargin.withNormalizedSeparator.trim, lambda.getSourcePsi.getText)
  }

  def testLocalVariableWithoutType(): Unit = {
    val initializer = uastExpressionFromText("1 + 2")
    val variable = uastElementFactory.createLocalVariable("a", null, initializer, false, null)

    val sourcePsi = variable.getSourcePsi
    assertTrue(sourcePsi.is[ScReferencePattern])
    assertEquals("var a = 1 + 2", sourcePsi.asInstanceOf[ScReferencePattern].nameContext.getText)
  }

  def testLocalVariableWithType(): Unit = {
    val initializer = uastExpressionFromText("1 + 2")
    val variable = uastElementFactory.createLocalVariable("a", PsiTypeConstants.Double, initializer, false, null)

    val sourcePsi = variable.getSourcePsi
    assertTrue(sourcePsi.is[ScReferencePattern])
    assertEquals("var a: Double = 1 + 2", sourcePsi.asInstanceOf[ScReferencePattern].nameContext.getText)
  }

  def testLocalFinalVariable(): Unit = {
    val initializer = uastExpressionFromText("1 + 2")
    val variable = uastElementFactory.createLocalVariable("a", PsiTypeConstants.Long, initializer, true, null)

    val sourcePsi = variable.getSourcePsi
    assertTrue(sourcePsi.is[ScReferencePattern])
    assertEquals("val a: Long = 1 + 2", sourcePsi.asInstanceOf[ScReferencePattern].nameContext.getText)
  }

  def testLocalVariableWithSuggestedName(): Unit = {
    val initializer = uastExpressionFromText("f(a) + 1")
    val variable = uastElementFactory.createLocalVariable(null, PsiTypeConstants.Int, initializer, true, null)

    val sourcePsi = variable.getSourcePsi
    assertTrue(sourcePsi.is[ScReferencePattern])
    assertEquals("val i: Int = f(a) + 1", sourcePsi.asInstanceOf[ScReferencePattern].nameContext.getText)
  }

  def testLongConstant(): Unit = {
    val const = uastElementFactory.createLongConstantExpression(7, null)
    assertEquals("7L", const.getSourcePsi.getText)
  }

  def testVeryLongConstant(): Unit = {
    val const = uastElementFactory.createLongConstantExpression(123456789000L, null)
    assertEquals("123456789000L", const.getSourcePsi.getText)
  }

  def testNullLiteral(): Unit = {
    val literal = uastElementFactory.createNullLiteral(null)
    assertTrue(literal.isNull)
    assertNull(literal.getValue)
    assertEquals("null", literal.getSourcePsi.getText)
  }

  def testParenthesizedExpression(): Unit = {
    val expr = uastExpressionFromText("a + b")
    val const = uastElementFactory.createParenthesizedExpression(expr, null)
    assertEquals("(a + b)", const.getSourcePsi.getText)
  }

  def testQualifiedReference(): Unit = {
    val refText = "java.util.List"
    val ref = uastElementFactory.createQualifiedReference(refText, null)
    assertEquals(refText, ref.getSourcePsi.getText)
  }

  def testReturnExpression(): Unit = {
    val expr = uastExpressionFromText("a + b")
    val returnExpr = uastElementFactory.createReturnExpression(expr, false, null)
    assertEquals("return a + b", returnExpr.getSourcePsi.getText)
  }

  def testSimpleReferenceFromName(): Unit = {
    val ref = uastElementFactory.createSimpleReference("a", null)
    assertEquals("a", ref.getSourcePsi.getText)
  }

  def testSimpleReferenceFromVariable(): Unit = {
    val refPattern = createScalaFileFromText("class Foo { val a: Int = ??? }", ScalaFeatures.onlyByVersion(version))
      .typeDefinitions.head
      .members.head
      .asInstanceOf[ScValueOrVariableDefinition]
      .bindings.head

    val variable = UastContextKt.toUElement(refPattern, classOf[UVariable])
    val ref = uastElementFactory.createSimpleReference(variable, null)
    assertEquals("a", ref.getSourcePsi.getText)
  }

  def testStringLiteral(): Unit = {
    val str = uastElementFactory.createStringLiteralExpression("foo", null).asInstanceOf[ULiteralExpression]
    assertTrue(str.isString)
    assertEquals("foo", str.getValue)
    assertEquals(""""foo"""", str.getSourcePsi.getText)
  }

  def testFunctionArgumentReplacement(): Unit = {
    def fileText(argumentText: String): String =
      s"""
         |object Test {
         |  def f(a: Any): Unit = {}
         |}
         |
         |object Main extends App {
         |  Test.f($argumentText)
         |}
         |""".stripMargin.withNormalizedSeparator.trim

    val file = myFixture.configureByText("test.scala", fileText("a"))

    val expression = findUElementByTextFromPsi[UCallExpression](file, "Test.f(a)")
    val newArgument = uastExpressionFromText("b").asInstanceOf[USimpleNameReferenceExpression]

    inWriteCommandAction {
      assertNotNull(expression.getValueArguments.get(0).replace(newArgument))
    }(getProject)

    val updated = expression.refreshed()
    assertNotNull("Could not update expression", updated)

    assertEquals("f", updated.getMethodName)
    val updatedArgument = updated.getValueArguments.get(0)
    assertTrue(updatedArgument.is[USimpleNameReferenceExpression])
    assertEquals("b", updatedArgument.asInstanceOf[USimpleNameReferenceExpression].getIdentifier)

    assertEquals(
      """
        |UMethodCall(name = f)
        |    UIdentifier (Identifier (f))
        |    USimpleNameReferenceExpression (identifier = b)
        |""".stripMargin.withNormalizedSeparator.trim, UastUtils.asRecursiveLogString(updated).withNormalizedSeparator.trim)

    myFixture.checkResult(fileText("b"), true)
  }

  def testInitializeField(): Unit = {
    val psiFile = myFixture.configureByText("MyClass.scala",
      s"""
         |class My${CARET}Class {
         |  var field: String
         |  def method(value: String) {
         |  }
         |}
         |""".stripMargin.withNormalizedSeparator.trim
    )

    val uClass = UastContextKt.toUElement(PsiTreeUtil.getParentOfType(psiFile.findElementAt(myFixture.getCaretOffset), classOf[ScClass]), classOf[UClass])
    val uField = uClass.getFields.head
    val uParameter = uClass.getMethods.find(_.getName == "method").get.getUastParameters.get(0)

    inWriteCommandAction {
      generatePlugin.initializeField(uField, uParameter)
    }(getProject)

    myFixture.checkResult(
      """
        |class MyClass {
        |  var field: String
        |  def method(value: String) {
        |    field = value
        |  }
        |}
        |""".stripMargin.withNormalizedSeparator.trim,
      true
    )
  }

  def testInitializeFieldWithSameName(): Unit = {
    val psiFile = myFixture.configureByText("MyClass.scala",
      s"""
         |class My${CARET}Class {
         |  var field: String
         |  def method(field: String) {
         |  }
         |}
         |""".stripMargin.withNormalizedSeparator.trim
    )

    val uClass = UastContextKt.toUElement(PsiTreeUtil.getParentOfType(psiFile.findElementAt(myFixture.getCaretOffset), classOf[ScClass]), classOf[UClass])
    val uField = uClass.getFields.head
    val uParameter = uClass.getMethods.find(_.getName == "method").get.getUastParameters.get(0)

    inWriteCommandAction {
      generatePlugin.initializeField(uField, uParameter)
    }(getProject)

    myFixture.checkResult(
      """
        |class MyClass {
        |  var field: String
        |  def method(field: String) {
        |    this.field = field
        |  }
        |}
        |""".stripMargin.withNormalizedSeparator.trim,
      true
    )
  }

  def testInitializeFieldInConstructor(): Unit = {
    val psiFile = myFixture.configureByText("MyClass.scala",
      s"""
         |class My${CARET}Class() {
         |  def this(value: String) = this()
         |  var field: String
         |}
         |""".stripMargin.withNormalizedSeparator.trim
    )

    val uClass = UastContextKt.toUElement(PsiTreeUtil.getParentOfType(psiFile.findElementAt(myFixture.getCaretOffset), classOf[ScClass]), classOf[UClass])
    val uField = uClass.getFields.head
    val uParameter = uClass.getMethods.find(m => m.isConstructor && !m.getUastParameters.isEmpty).get.getUastParameters.get(0)

    inWriteCommandAction {
      generatePlugin.initializeField(uField, uParameter)
    }(getProject)

    myFixture.checkResult(
      """
        |class MyClass() {
        |  def this(value: String) = {
        |    this()
        |    field = value
        |  }
        |  var field: String
        |}
        |""".stripMargin.withNormalizedSeparator.trim,
      true
    )
  }

  def testInitializeFieldInPrimaryConstructor(): Unit = {
    val psiFile = myFixture.configureByText("MyClass.scala",
      s"""
         |class My${CARET}Class(value: String) {
         |  val field: String
         |}
         |""".stripMargin.withNormalizedSeparator.trim
    )

    val uClass = UastContextKt.toUElement(PsiTreeUtil.getParentOfType(psiFile.findElementAt(myFixture.getCaretOffset), classOf[ScClass]), classOf[UClass])
    val uField = uClass.getFields.head
    val uParameter = uClass.getMethods.find(m => m.isConstructor && !m.getUastParameters.isEmpty).get.getUastParameters.get(0)

    inWriteCommandAction {
      generatePlugin.initializeField(uField, uParameter)
    }(getProject)

    myFixture.checkResult(
      """
        |class MyClass(value: String) {
        |  val field: String = value
        |}
        |""".stripMargin.withNormalizedSeparator.trim,
      true
    )
  }

  def testInitializeFieldInPrimaryConstructorWithSameName(): Unit = {
    val psiFile = myFixture.configureByText("MyClass.scala",
      s"""
         |class My${CARET}Class(field: String) {
         |  private val field: String
         |}
         |""".stripMargin.withNormalizedSeparator.trim
    )

    val uClass = UastContextKt.toUElement(PsiTreeUtil.getParentOfType(psiFile.findElementAt(myFixture.getCaretOffset), classOf[ScClass]), classOf[UClass])
    val uField = uClass.getFields.head
    val uParameter = uClass.getMethods.find(m => m.isConstructor && !m.getUastParameters.isEmpty).get.getUastParameters.get(0)

    inWriteCommandAction {
      generatePlugin.initializeField(uField, uParameter)
    }(getProject)

    myFixture.checkResult(
      """
        |class MyClass(private val field: String) {
        |}
        |""".stripMargin.withNormalizedSeparator.trim,
      true
    )
  }

  @Nullable
  private def uastExpressionFromText(text: String, context: PsiElement = null): UExpression = {
    val scExpression = createScalaElementFromTextWithContext[ScExpression](text, context)
    UastContextKt.toUElement(scExpression.orNull, classOf[UExpression])
  }

  //  private def psiTypeFromJavaTypeText(text: String): PsiType =
  //    JavaPsiFacade.getElementFactory(projectContext).createTypeFromText(text, null)

  private def javaList[T](elements: T*): ju.List[T] = ju.Arrays.asList(elements: _*)

  private implicit def projectContext: ProjectContext = myFixture.getProject
}

object ScalaUastGenerationTest {
  implicit final class UElementExt[T <: UElement](private val element: T) extends AnyVal {
    @Nullable def replace[U <: UElement : ClassTag](newElement: U): U = {
      val plugin = UastCodeGenerationPlugin.byLanguage(element.getLang)
      if (plugin == null) null.asInstanceOf[U]
      else plugin.replace(element, newElement, getClassOf[U])
    }

    @Nullable def refreshed()(implicit ct: ClassTag[T]): T = {
      val source = element.getSourcePsi
      if (source == null) null.asInstanceOf[T]
      else {
        assert(source.isValid, s"psi $source of class ${source.getClass} should be valid, containing file = ${source.getContainingFile}")
        UastContextKt.toUElement(source, getClassOf[T])
      }
    }
  }

  private def getClassOf[T: ClassTag : NotNothing]: Class[T] = classTag[T].runtimeClass.asInstanceOf[Class[T]]
}
