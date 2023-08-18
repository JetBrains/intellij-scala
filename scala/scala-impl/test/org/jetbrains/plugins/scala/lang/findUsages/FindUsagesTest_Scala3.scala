package org.jetbrains.plugins.scala.lang.findUsages

import org.jetbrains.plugins.scala.ScalaVersion

class FindUsagesTest_Scala3 extends FindUsagesTest_Scala2 {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

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
       |  def this() = this("42")
       |  def this(i: Int) = this(i.toString)
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
       |  def this() = this("42")
       |  def this(i: Int) = this(i.toString)
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
}