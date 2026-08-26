package org.jetbrains.plugins.scala.lang.findUsages

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.lang.findUsages.LanguageFindUsages
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageViewUtil
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.junit.Assert.{assertEquals, assertFalse}

class FindUsagesTest_Scala3 extends FindUsagesTest_Scala2 {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  def testExtensionMethodUsagePresentation(): Unit = {
    myFixture.configureByText(
      "Extension.scala",
      """package demo.extensions
        |
        |class User
        |
        |object Definitions:
        |  extension (target: User)
        |    def present(suffix: String): String = ???
        |
        |  def regular(suffix: String): String = ???
        |""".stripMargin
    )

    val functions = PsiTreeUtil.findChildrenOfType(getFile, classOf[ScFunction]).toArray(new Array[ScFunction](0))
    val extension = functions.find(_.isExtensionMethod).orNull
    val regular = functions.find(!_.isExtensionMethod).orNull

    assertEquals("User.present(suffix: String)", LanguageFindUsages.getDescriptiveName(extension))
    assertEquals("User.present(suffix: String)", UsageViewUtil.getLongName(extension))
    assertEquals("User.present", UsageViewUtil.getShortName(extension))
    assertEquals("User.present(suffix: String)", new ScalaFindUsagesProvider().getNodeText(extension, useFullName = false))

    val target = new PsiElement2UsageTargetAdapter(extension, true)
    assertEquals("User.present(suffix: String)", target.getPresentation.getPresentableText)
    assertEquals("demo.extensions.Definitions", target.getPresentation.getLocationString)

    assertFalse(regular.isExtensionMethod)
    assertEquals("regular", regular.getPresentation.getPresentableText)
    assertEquals("(demo.extensions.Definitions)", regular.getPresentation.getLocationString)
    assertEquals("regular(String) of demo.extensions.Definitions", LanguageFindUsages.getDescriptiveName(regular))
    assertEquals("regular(String)", UsageViewUtil.getLongName(regular))
    assertEquals("regular", UsageViewUtil.getShortName(regular))
  }

  def testTypeParameterInEnumCaseUsedInScalaDoc(): Unit = doTest(
    s"""enum TestEnum [MyTypeParameter](myParameter: Int) {
       |  /**
       |   * @param myParameterInner42 parameter description
       |   * @tparam ${start}MyTypeParameterInner$end type parameter description
       |   */
       |  case EnumMember[${CARET}MyTypeParameterInner](myParameterInner42: Int)
       |    extends TestEnum[${start}MyTypeParameterInner$end](myParameterInner42)
       |}
       |""".stripMargin
  )

  def testParameterInEnumCaseUsedInScalaDoc(): Unit = doTest(
    s"""enum TestEnum [MyTypeParameter](myParameter: Int) {
       |  /**
       |   * @param ${start}myParameterInner42$end parameter description
       |   * @tparam MyTypeParameterInner type parameter description
       |   */
       |  case EnumMember[MyTypeParameterInner](${CARET}myParameterInner42: Int)
       |    extends TestEnum[MyTypeParameterInner](${start}myParameterInner42$end)
       |}
       |""".stripMargin
  )

  def testUniversalApplySyntax_ClassWithEmptyConstructor(): Unit = doTest(
    s"""class ${CARET}MyClassWithEmptyConstructor()
       |
       |new ${start}MyClassWithEmptyConstructor$end()
       |${start}MyClassWithEmptyConstructor$end()
       |""".stripMargin
  )

  def testUniversalApplySyntax_ClassWithNonEmptyConstructor(): Unit = doTest(
    s"""class ${CARET}MyClassWithNonEmptyConstructor(p: String)
       |
       |new ${start}MyClassWithNonEmptyConstructor$end("42")
       |${start}MyClassWithNonEmptyConstructor$end("42")
       |""".stripMargin
  )

  def testUniversalApplySyntax_ClassWithMultipleConstructors(): Unit = doTest(
    s"""class ${CARET}MyClassWithMultipleConstructors(p: String) {
       |  def this() = ${start}this$end("42")
       |  def this(i: Int) = ${start}this$end(i.toString)
       |}
       |
       |new ${start}MyClassWithMultipleConstructors$end()
       |new ${start}MyClassWithMultipleConstructors$end("42")
       |new ${start}MyClassWithMultipleConstructors$end(23)
       |${start}MyClassWithMultipleConstructors$end()
       |${start}MyClassWithMultipleConstructors$end("42")
       |${start}MyClassWithMultipleConstructors$end(23)
       |""".stripMargin
  )

  def testUniversalApplySyntax_ClassWithMultipleConstructorsAndApplyMethodsInCompanion(): Unit = doTest(
    s"""class ${CARET}MyClassWithMultipleConstructorsAndApplyMethodsInCompanion(p: String) {
       |  def this() = ${start}this$end("42")
       |  def this(i: Int) = ${start}this$end(i.toString)
       |}
       |object MyClassWithMultipleConstructorsAndApplyMethodsInCompanion {
       |  def apply(i: Int, s: String): ${start}MyClassWithMultipleConstructorsAndApplyMethodsInCompanion$end = ???
       |}
       |
       |new ${start}MyClassWithMultipleConstructorsAndApplyMethodsInCompanion$end()
       |new ${start}MyClassWithMultipleConstructorsAndApplyMethodsInCompanion$end("42")
       |new ${start}MyClassWithMultipleConstructorsAndApplyMethodsInCompanion$end(23)
       |
       |//Invalid code, "constructor proxy" are not generated in this case (see https://docs.scala-lang.org/scala3/reference/other-new-features/creator-applications.html)
       |//MyClassWithMultipleConstructorsAndApplyMethodsInCompanion()
       |//MyClassWithMultipleConstructorsAndApplyMethodsInCompanion("42")
       |//MyClassWithMultipleConstructorsAndApplyMethodsInCompanion(23)
       |
       |MyClassWithMultipleConstructorsAndApplyMethodsInCompanion(23, "42")
       |MyClassWithMultipleConstructorsAndApplyMethodsInCompanion.apply(23, "42")
       |""".stripMargin
  )

  def testClassWithMultipleConstructors_FindFromDefinition_UniversalApplySyntax(): Unit = doTest(
    s"""class ${CARET}MyClass(s: String) {
       |  def this(x: Int) = ${start}this$end(x.toString)
       |  def this(x: Short) = this(x.toInt)
       |}
       |${start}MyClass$end("test1")
       |${start}MyClass$end("test2")
       |${start}MyClass$end(42)
       |${start}MyClass$end(23)
       |val x: ${start}MyClass$end = ???
       |""".stripMargin
  )

  def testClassWithMultipleConstructors_FromPrimaryConstructorInvocation_UniversalApplySyntax(): Unit = doTest(
    s"""class MyClass(s: String) {
       |  def this(x: Int) = ${start}this$end(x.toString)
       |  def this(x: Short) = this(x.toInt)
       |}
       |$CARET${start}MyClass$end("test1")
       |${start}MyClass$end("test2")
       |MyClass(42)
       |MyClass(23)
       |val x: MyClass = ???
       |""".stripMargin
  )

  def testClassWithMultipleConstructors_FromPrimaryConstructorInvocation_UniversalApplySyntax_WithEmptyParameters(): Unit = doTest(
    s"""class MyClass {
       |  def this(x: Int) = ${start}this$end()
       |  def this(x: Short) = this(x.toInt)
       |}
       |$CARET${start}MyClass$end()
       |${start}MyClass$end()
       |MyClass(42)
       |MyClass(23)
       |val x: MyClass = ???
       |""".stripMargin
  )

  def testClassWithMultipleConstructors_FromSecondaryConstructorInvocation_UniversalApplySyntax(): Unit = doTest(
    s"""class MyClass(s: String) {
       |  def this(x: Int) = this(x.toString)
       |  def this(x: Short) = ${start}this$end(x.toInt)
       |}
       |
       |MyClass("test1")
       |MyClass("test2")
       |$CARET${start}MyClass$end(42)
       |${start}MyClass$end(23)
       |val x: MyClass = ???
       |""".stripMargin
  )

  def testFindExtensionOverriders(): Unit = {
    doTest(
      s"""
         |trait FindMyMembers {
         |  extension (x: String) def ${CARET}findMyExtension: String
         |  def methodInTrait(): Unit = {
         |    println("findMyExtension = " + "42".${start}findMyExtension$end)
         |  }
         |}
         |
         |class FindMyMembersImpl extends FindMyMembers {
         |  extension (x: String) override def findMyExtension: String = ???
         |
         |  def methodInImpl(): Unit = {
         |    println("findMyExtension = " + "42".${start}findMyExtension$end)
         |  }
         |}
      """.stripMargin)
  }

  def testFindUsingParameter(): Unit =
    doTest(
      s"""
         |object Test {
         |  def foo()(using ${CARET}x: Int) = {
         |    ${start}foo()$end
         |    foo()(using ${start}x$end)
         |  }
         |}
         |""".stripMargin
    )

  def testFindGiven(): Unit =
    doTest(
      s"""
         |object Test {
         |  def foo()(implicit x: Int) = ()
         |
         |  {
         |    given ${CARET}x: Int = 42
         |
         |    ${start}foo()$end
         |    foo()(${start}x$end)
         |  }
         |}
         |""".stripMargin
    )

  def testFindTypeParameterUsedAsNamedTypeArgumentName(): Unit = doTest(
    s"""
       |import scala.language.experimental.namedTypeArguments
       |
       |def construct[${CARET}A, B]: Unit = ()
       |
       |construct[${start}A$end = Int, B = String]
       |construct[B = String, ${start}A$end = Int]
       |""".stripMargin
  )

  def testFindTypeParameterUsedInInterleavedClauses(): Unit = doTest(
    s"""
       |def combine[${CARET}A](first: ${start}A$end)[B](second: B)(fallback: ${start}A$end): ${start}A$end = fallback
       |""".stripMargin
  )

  def testFindTypeParameterUsedAsNamedTypeArgumentNameInInterleavedClause(): Unit = doTest(
    s"""
       |import scala.language.experimental.namedTypeArguments
       |
       |def combine[A](first: A)[${CARET}B](second: ${start}B$end): Unit = ()
       |
       |combine[Int](1)[${start}B$end = String]("text")
       |""".stripMargin
  )
}
