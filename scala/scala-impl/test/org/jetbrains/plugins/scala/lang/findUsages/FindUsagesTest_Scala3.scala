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
}