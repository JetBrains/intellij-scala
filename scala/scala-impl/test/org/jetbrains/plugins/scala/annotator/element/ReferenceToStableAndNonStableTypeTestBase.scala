package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingTestBase

abstract class ReferenceToStableAndNonStableTypeTestBase extends ScalaHighlightingTestBase {

  def testValType(): Unit =
    assertNoErrors(
      """//noinspection NotImplementedCode
        |abstract class Example1 {
        |  val field: String = ???
        |
        |  //referenced
        |  val v1: "literal type" = ???
        |  val v2: field.type = ???
        |  val v3_Abstract: 4
        |
        |  //annotations
        |  val a1 : v1.type = ???
        |  val a2 : v2.type = ???
        |  val a3 : v3_Abstract.type = ???
        |
        |  val b1 : Example1.this.v1.type = ???
        |  val b2 : Example1.this.v2.type = ???
        |  val b3 : Example1.this.v3_Abstract.type = ???
        |}
        |""".stripMargin
    )

  def testValType_ReferencedFromDifferentContexts(): Unit =
    assertNoErrors(
      """//noinspection NotImplementedCode
        |abstract class Parent {
        |  val field: String = ???
        |
        |  //referenced
        |  val v1: "literal type" = ???
        |
        |  //annotation
        |  val a1 : v1.type = ???
        |  val b1 : Parent.this.v1.type = ???
        |}
        |
        |object Child extends Parent {
        |  val c1 : Child.super.v1.type = ???
        |}
        |
        |object AnotherObject extends Parent {
        |  val d1 : AnotherObject.super.v1.type = ???
        |  val d2 : AnotherObject.v1.type = ???
        |  val d3 : Child.v1.type = ???
        |}
        |""".stripMargin
    )

  protected val VarWithSingletonType_Code =
    """//noinspection NotImplementedCode
      |abstract class Example1 {
      |  val field: String = ???
      |
      |  //referenced
      |  var v1: "literal type" = ???
      |  var v2: field.type = ???
      |  var v3_Abstract: 4
      |
      |  //annotations
      |  val a1 : v1.type = ???
      |  val a2 : v2.type = ???
      |  val a3 : v3_Abstract.type = ???
      |
      |  val b1 : Example1.this.v1.type = ???
      |  val b2 : Example1.this.v2.type = ???
      |  val b3 : Example1.this.v3_Abstract.type = ???
      |}
      |""".stripMargin
  def testVarWithSingletonType(): Unit

  protected val VarWithSingletonType_ReferencedFromDifferentContexts_Code =
    """//noinspection NotImplementedCode
      |abstract class Parent {
      |  val field: String = ???
      |
      |  //referenced
      |  var v1: "literal type" = ???
      |
      |  //annotation
      |  val a1 : v1.type = ???
      |  val b1 : Parent.this.v1.type = ???
      |}
      |
      |object Child extends Parent {
      |  val c1 : Child.super.v1.type = ???
      |}
      |
      |object AnotherObject extends Parent {
      |  val d1 : AnotherObject.super.v1.type = ???
      |  val d2 : AnotherObject.v1.type = ???
      |  val d3 : Child.v1.type = ???
      |}
      |""".stripMargin
  def testVarWithSingletonType_ReferencedFromDifferentContexts(): Unit

  def testVarWithNonSingletonType(): Unit =
    assertErrorsText(
      """//noinspection NotImplementedCode
        |abstract class Example1 {
        |  //referenced
        |  var v1: String = ???
        |
        |  //annotations
        |  val a1 : v1.type = ???
        |}
        |""".stripMargin,
      """Error(v1.type,Stable identifier required but v1.type found)
        |""".stripMargin
    )

  protected val ReferenceToFunctionType_ParameterlessFunctionWithSingletonReturnType_Code =
    """//noinspection NotImplementedCode
      |abstract class Example1 {
      |  val field: String = ???
      |
      |  //referenced
      |  def f1: "literal type" = ???
      |  def f2: field.type = ???
      |  def f3_Abstract: 4
      |
      |  //annotations
      |  val a1 : f1.type = ???
      |  val a2 : f2.type = ???
      |  val a3 : f3_Abstract.type = ???
      |
      |  val b1 : Example1.this.f1.type = ???
      |  val b2 : Example1.this.f2.type = ???
      |  val b3 : Example1.this.f3_Abstract.type = ???
      |}
      |""".stripMargin
  def testParameterlessFunction_WithSingletonReturnType(): Unit

  protected val LazyVal_Code =
    """class A {
      |  lazy val v1_WithExplicitType: 42 = 42
      |  lazy val v2_WithoutExplicitType = 42
      |
      |  val ref1: v1_WithExplicitType.type = ???
      |  val ref2: v2_WithoutExplicitType.type = ???
      |}
      |""".stripMargin
  def testReferenceToLazyVal(): Unit

  protected val FinalLazyVal_Code =
    """class A {
      |  final lazy val v1_WithExplicitType: 42 = 42
      |  final lazy val v2_WithoutExplicitType = 42
      |
      |  val ref1: v1_WithExplicitType.type = ???
      |  val ref2: v2_WithoutExplicitType.type = ???
      |}
      |""".stripMargin
  def testReferenceToFinalLazyVal(): Unit = {
    assertNoErrors(FinalLazyVal_Code)
  }
}

class ReferenceToStableAndNonStableTypeTest_Scala2 extends ReferenceToStableAndNonStableTypeTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  override def testParameterlessFunction_WithSingletonReturnType(): Unit = {
    assertErrorsText(
      ReferenceToFunctionType_ParameterlessFunctionWithSingletonReturnType_Code,
      """Error(f1.type,Stable identifier required but f1.type found)
        |Error(f2.type,Stable identifier required but f2.type found)
        |Error(f3_Abstract.type,Stable identifier required but f3_Abstract.type found)
        |
        |Error(Example1.this.f1.type,Stable identifier required but Example1.this.f1.type found)
        |Error(Example1.this.f2.type,Stable identifier required but Example1.this.f2.type found)
        |Error(Example1.this.f3_Abstract.type,Stable identifier required but Example1.this.f3_Abstract.type found)""".stripMargin
    )
  }

  override def testReferenceToLazyVal(): Unit =
    assertNoErrors(LazyVal_Code)

  override def testVarWithSingletonType(): Unit =
    assertErrorsText(
      VarWithSingletonType_Code,
      """Error(v1.type,Stable identifier required but v1.type found)
        |Error(v2.type,Stable identifier required but v2.type found)
        |Error(v3_Abstract.type,Stable identifier required but v3_Abstract.type found)
        |
        |Error(Example1.this.v1.type,Stable identifier required but Example1.this.v1.type found)
        |Error(Example1.this.v2.type,Stable identifier required but Example1.this.v2.type found)
        |Error(Example1.this.v3_Abstract.type,Stable identifier required but Example1.this.v3_Abstract.type found)""".stripMargin
    )

  override def testVarWithSingletonType_ReferencedFromDifferentContexts(): Unit =
    assertErrorsText(
      VarWithSingletonType_ReferencedFromDifferentContexts_Code,
      """Error(v1.type,Stable identifier required but v1.type found)
        |Error(Parent.this.v1.type,Stable identifier required but Parent.this.v1.type found)
        |Error(Child.super.v1.type,Stable identifier required but Child.super.v1.type found)
        |Error(AnotherObject.super.v1.type,Stable identifier required but AnotherObject.super.v1.type found)
        |Error(AnotherObject.v1.type,Stable identifier required but AnotherObject.v1.type found)
        |Error(Child.v1.type,Stable identifier required but Child.v1.type found)
        |""".stripMargin
    )
}

class ReferenceToStableAndNonStableTypeTest_Scala3 extends ReferenceToStableAndNonStableTypeTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0

  override def testValType(): Unit =
    assertNoErrors(
      """//noinspection NotImplementedCode
        |abstract class Example1 {
        |  val field: String = ???
        |
        |  //referenced
        |  val v1: "literal type" = ???
        |  val v2: field.type = ???
        |  val v3_Abstract: 4
        |  lazy val v4_Lazy_Abstract: 42
        |
        |
        |  //annotations
        |  val a1 : v1.type = ???
        |  val a2 : v2.type = ???
        |  val a3 : v3_Abstract.type = ???
        |  val a4 : v4_Lazy_Abstract.type = ???
        |
        |  val b1 : Example1.this.v1.type = ???
        |  val b2 : Example1.this.v2.type = ???
        |  val b3 : Example1.this.v3_Abstract.type = ???
        |  val b4 : Example1.this.v4_Lazy_Abstract.type = ???
        |}
        |""".stripMargin
    )

  def testValType_TopLevel(): Unit = {
    assertNoErrors(
      """val field: String = ???
        |
        |//referenced
        |val v1: "literal type" = ???
        |val v2: field.type = ???
        |
        |//annotations
        |val a1 : v1.type = ???
        |val a2 : v2.type = ???""".stripMargin
    )
  }

  override def testVarWithSingletonType(): Unit =
    assertNoErrors(VarWithSingletonType_Code)

  override def testVarWithSingletonType_ReferencedFromDifferentContexts(): Unit =
    assertNoErrors(VarWithSingletonType_ReferencedFromDifferentContexts_Code)

  def testVarWithSingletonType_Code_TopLevel(): Unit =
    assertNoErrors(
      """val field: String = ???
        |
        |//referenced
        |var v1: "literal type" = ???
        |var v2: field.type = ???
        |var (v3: "literal type", v4: String) = ???
        |
        |//annotations
        |val a1 : v1.type = ???
        |val a2 : v2.type = ???
        |val a3 : v3.type = ???
        |""".stripMargin
    )

  def testVarWithSingletonType_Code_TopLevel_InPackage(): Unit =
    assertNoErrors(
      """package aaa.bbb.ccc
        |
        |val field: String = ???
        |
        |//referenced
        |var v1: "literal type" = ???
        |var v2: field.type = ???
        |
        |//annotations
        |val a1 : v1.type = ???
        |val a2 : v2.type = ???
        |""".stripMargin
    )

  def testVarWithNonSingletonType_TopLevel(): Unit =
    assertErrorsText(
      """//referenced
        |var v1: String = ???
        |var (v2: "literal type", v3: String) = ???
        |
        |//annotations
        |val a1 : v1.type = ???
        |val a3 : v3.type = ???
        |""".stripMargin,
      """Error(v1.type,Stable identifier required but v1.type found)
        |Error(v3.type,Stable identifier required but v3.type found)
        |""".stripMargin
    )


  def testVarWithNonSingletonType_TopLevel_InPackage(): Unit =
    assertErrorsText(
      """package aaa.bbb.ccc
        |
        |//referenced
        |var v1: String = ???
        |
        |//annotations
        |val a1 : v1.type = ???
        |""".stripMargin,
      """Error(v1.type,Stable identifier required but v1.type found)
        |""".stripMargin
    )

  override def testParameterlessFunction_WithSingletonReturnType(): Unit =
    assertNoErrors(
      ReferenceToFunctionType_ParameterlessFunctionWithSingletonReturnType_Code
    )

  def testParameterlessFunction_WithSingletonReturnType_TopLevel(): Unit =
    assertNoErrors(
      """val field: String = ???
        |
        |//referenced
        |def f1: "literal type" = ???
        |def f2: field.type = ???
        |
        |//annotations
        |val a1 : f1.type = ???
        |val a2 : f2.type = ???
        |""".stripMargin
    )

  def testParameterlessFunction_WithSingletonReturnType_WithTypeParameters_AtDifferentContexts(): Unit =
    assertNoErrors(
      """//noinspection NotImplementedCode
        |abstract class Example1 {
        |  //referenced
        |  def foo_WithTypeParameter[T1, T2]: 4 = ???
        |
        |  //as annotated expression
        |  ??? : foo_WithTypeParameter.type
        |  println(??? : foo_WithTypeParameter.type)
        |  //as type/return type
        |  val vl: foo_WithTypeParameter.type = ???
        |  var vr: foo_WithTypeParameter.type = ???
        |  def f1: foo_WithTypeParameter.type = ???
        |  //as parameter type
        |  def f2(p: foo_WithTypeParameter.type) = ???
        |  //as type parameter bound
        |  def f3[T >: foo_WithTypeParameter.type <: foo_WithTypeParameter.type] = ???
        |}
        |""".stripMargin
    )

  def testParameterlessFunction_WithSingletonReturnType_WithTypeParameters_AtDifferentContexts_TopLevel(): Unit =
    assertNoErrors(
      """//referenced
        |def foo_WithTypeParameter[T1, T2]: 4 = ???
        |
        |//as type/return type
        |val vl: foo_WithTypeParameter.type = ???
        |var vr: foo_WithTypeParameter.type = ???
        |def f1: foo_WithTypeParameter.type = ???
        |//as parameter type
        |def f2(p: foo_WithTypeParameter.type) = ???
        |//as type parameter bound
        |def f3[T >: foo_WithTypeParameter.type <: foo_WithTypeParameter.type] = ???
        |""".stripMargin
    )

  def testParameterlessFunction_WithSingletonReturnType_DifferentSimpleLiteralTypes(): Unit =
    assertNoErrors(
      """//noinspection NotImplementedCode
        |abstract class Example1 {
        |  //referenced
        |  def f0: 42 = ???
        |  def f1: 42 = ???
        |  def f2: 42f = ???
        |  def f3: 42d = ???
        |  def f4: 42L = ???
        |  def f5: 42.0 = ???
        |  def f6: -42.0D = ???
        |  def f7: true = ???
        |  def f8: 'c' = ???
        |  def f9: "string" = ???
        |
        |  //annotations
        |  val a1 : f1.type = ???
        |  val a2 : f2.type = ???
        |  val a3 : f3.type = ???
        |  val a4 : f4.type = ???
        |  val a5 : f5.type = ???
        |  val a6 : f6.type = ???
        |  val a7 : f7.type = ???
        |  val a8 : f8.type = ???
        |}
        |""".stripMargin
    )

  def testParameterlessFunction_WithSingletonReturnType_DifferentSimpleLiteralTypes_TopLevel(): Unit =
    assertNoErrors(
      """//referenced
        |def f0: 42 = ???
        |def f1: 42 = ???
        |def f2: 42f = ???
        |def f3: 42d = ???
        |def f4: 42L = ???
        |def f5: 42.0 = ???
        |def f6: -42.0D = ???
        |def f7: true = ???
        |def f8: 'c' = ???
        |def f9: "string" = ???
        |
        |//annotations
        |val a1 : f1.type = ???
        |val a2 : f2.type = ???
        |val a3 : f3.type = ???
        |val a4 : f4.type = ???
        |val a5 : f5.type = ???
        |val a6 : f6.type = ???
        |val a7 : f7.type = ???
        |val a8 : f8.type = ???
        |""".stripMargin
    )

  def testParameterlessFunction_WithNonSingletonReturnType_NonSimpleLiteralType(): Unit =
    assertErrorsText(
      """//noinspection NotImplementedCode
        |abstract class Example1 {
        |  // not simple literal
        |  def g1: null = ???
        |  def g2: s"interpolated" = ???
        |  def g3: s"interpolated ${1 + 2}" = ???
        |  def g4: 'Symbol = ???
        |
        |  // not simple literal
        |  val a1 : g1.type = ???
        |  val a2 : g2.type = ???
        |  val a3 : g3.type = ???
        |  val a4 : g4.type = ???
        |}
        |""".stripMargin,
      """Error(null,An identifier expected, but 'null' found)
        |Error(s"interpolated",An identifier expected, but string interpolator found)
        |Error(s"interpolated ${1 + 2}",An identifier expected, but string interpolator found)
        |Error('Symbol,An identifier expected, but quoted identifier found)
        |
        |Error(g1.type,Stable identifier required but g1.type found)
        |Error(g2.type,Stable identifier required but g2.type found)
        |Error(g3.type,Stable identifier required but g3.type found)
        |Error(g4.type,Stable identifier required but g4.type found)
        |""".stripMargin
    )

  def testParameterlessFunction_WithNonSingletonReturnType_NonSimpleLiteralType_TopLevel(): Unit =
    assertErrorsText(
      """// not simple literal
        |def g1: null = ???
        |def g2: s"interpolated" = ???
        |def g3: s"interpolated ${1 + 2}" = ???
        |def g4: 'Symbol = ???
        |
        |// not simple literal
        |val a1 : g1.type = ???
        |val a2 : g2.type = ???
        |val a3 : g3.type = ???
        |val a4 : g4.type = ???
        |""".stripMargin,
      """Error(null,An identifier expected, but 'null' found)
        |Error(s"interpolated",An identifier expected, but string interpolator found)
        |Error(s"interpolated ${1 + 2}",An identifier expected, but string interpolator found)
        |Error('Symbol,An identifier expected, but quoted identifier found)
        |Error(g1.type,Stable identifier required but g1.type found)
        |Error(g2.type,Stable identifier required but g2.type found)
        |Error(g3.type,Stable identifier required but g3.type found)
        |Error(g4.type,Stable identifier required but g4.type found)
        |""".stripMargin
    )

  def testParameterlessFunction_WithNonSingletonReturnType_NonLiteralType(): Unit =
    assertErrorsText(
      """//noinspection NotImplementedCode
        |abstract class Example1 {
        |  //referenced
        |  def g1: String = ???
        |  def g2[T]: T = ???
        |
        |  //annotations
        |  val a1 : g1.type = ???
        |  val a2 : g2.type = ???
        |}
        |""".stripMargin,
      """Error(g1.type,Stable identifier required but g1.type found)
        |Error(g2.type,Stable identifier required but g2.type found)
        |""".stripMargin
    )

  def testParameterlessFunction_WithNonSingletonReturnType_NonLiteralType_TopLevel(): Unit =
    assertErrorsText(
      """//referenced
        |def g1: String = ???
        |def g2[T]: T = ???
        |
        |//annotations
        |val a1 : g1.type = ???
        |val a2 : g2.type = ???
        |""".stripMargin,
      """Error(g1.type,Stable identifier required but g1.type found)
        |Error(g2.type,Stable identifier required but g2.type found)
        |""".stripMargin
    )

  def testFunctionWithValueParameters(): Unit =
    assertErrorsText(
      """//noinspection NotImplementedCode
        |abstract class Example1 {
        |  //referenced
        |  def g1(): 42 = ???
        |  def g2(x: Int): 42 = ???
        |  def g3(using x: Int): 42 = ???
        |  def g4(implicit x: Int): 42 = ???
        |
        |  //annotations
        |  val a1 : g1.type = ???
        |  val a2 : g2.type = ???
        |  val a3 : g3.type = ???
        |  val a4 : g4.type = ???
        |}""".stripMargin,
      """Error(g1.type,Stable identifier required but g1.type found)
        |Error(g2.type,Stable identifier required but g2.type found)
        |Error(g3.type,Stable identifier required but g3.type found)
        |Error(g4.type,Stable identifier required but g4.type found)
        |""".stripMargin
    )

  def testFunctionWithValueParameters_TopLevel(): Unit =
    assertErrorsText(
      """//referenced
        |def g1(): 42 = ???
        |def g2(x: Int): 42 = ???
        |def g3(using x: Int): 42 = ???
        |def g4(implicit x: Int): 42 = ???
        |
        |//annotations
        |val a1 : g1.type = ???
        |val a2 : g2.type = ???
        |val a3 : g3.type = ???
        |val a4 : g4.type = ???
        |""".stripMargin,
      """Error(g1.type,Stable identifier required but g1.type found)
        |Error(g2.type,Stable identifier required but g2.type found)
        |Error(g3.type,Stable identifier required but g3.type found)
        |Error(g4.type,Stable identifier required but g4.type found)
        |""".stripMargin
    )

  override def testReferenceToLazyVal(): Unit =
    assertErrorsText(
      LazyVal_Code,
      """Error(v2_WithoutExplicitType.type,Stable identifier required but v2_WithoutExplicitType.type found)
        |""".stripMargin
    )

  def testReferenceToLazyVal_TopLevel(): Unit =
    assertNoErrors(
      """lazy val v1 = 42
        |val ref1: v1.type = v1
        |
        |final lazy val v2 = 42
        |val ref2: v2.type = v2
        |""".stripMargin
    )

  def testEnumCaseType(): Unit =
    assertNoErrors(
      """object WrapperObject {
        |  enum MyEnum {
        |    case CaseA, CaseB
        |  }
        |
        |  val a1: MyEnum.CaseA.type = MyEnum.CaseA
        |  val a2: WrapperObject.MyEnum.CaseA.type = MyEnum.CaseA
        |
        |  {
        |    import MyEnum._
        |    val a3: CaseA.type = MyEnum.CaseA
        |  }
        |}
        |
        |class WrapperClass {
        |  enum MyEnum {
        |    case CaseA, CaseB
        |  }
        |
        |  val a1: MyEnum.CaseA.type = MyEnum.CaseA
        |  val a2: WrapperClass.this.MyEnum.CaseA.type = MyEnum.CaseA
        |
        |  {
        |    import MyEnum._
        |    val a3: CaseA.type = MyEnum.CaseA
        |  }
        |}
        |""".stripMargin
    )
}
