package org.jetbrains.plugins.scala.lang.findUsages

import org.jetbrains.plugins.scala.ScalaVersion

class FindUsagesTest_Scala3 extends FindUsagesTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  def testTypeParameterInEnumCaseUsedInScalaDoc(): Unit = doTest(
    s"""enum TestEnum [MyTypeParameter](myParameter: Int) {
       |  /**
       |   * @param myParameterInner42 parameter description
       |   * @tparam ${start(0)}MyTypeParameterInner${end(0)} type parameter description
       |   */
       |  case EnumMember[${CARET}MyTypeParameterInner](myParameterInner42: Int)
       |    extends TestEnum[${start(1)}MyTypeParameterInner${end(1)}](myParameterInner42)
       |}
       |""".stripMargin
  )

  def testParameterInEnumCaseUsedInScalaDoc(): Unit = doTest(
    s"""enum TestEnum [MyTypeParameter](myParameter: Int) {
       |  /**
       |   * @param ${start(0)}myParameterInner42${end(0)} parameter description
       |   * @tparam MyTypeParameterInner type parameter description
       |   */
       |  case EnumMember[MyTypeParameterInner](${CARET}myParameterInner42: Int)
       |    extends TestEnum[MyTypeParameterInner](${start(1)}myParameterInner42${end(1)})
       |}
       |""".stripMargin
  )
}