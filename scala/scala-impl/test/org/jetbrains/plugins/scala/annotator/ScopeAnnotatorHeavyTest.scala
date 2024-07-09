package org.jetbrains.plugins.scala.annotator

import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaVersion

/**
 * See also [[ScopeAnnotatorTestBase]]
 */
abstract class ScopeAnnotatorHeavyTestBase extends ScalaHighlightingTestBase

class ScopeAnnotatorHeavyTest extends ScopeAnnotatorHeavyTestBase {

  override protected def supportedIn(version: ScalaVersion) =
    version == ScalaVersion.Latest.Scala_2_13

  override def annotate(element: PsiElement)
                       (implicit holder: ScalaAnnotationHolder): Unit =
    ScopeAnnotator.annotateScope(element)

  def testTypeErasure(): Unit =
    assertErrorsText(
      """class Foo
        |class Bar
        |trait Box[T]
        |
        |object Test {
        |  def f(a: Seq[Foo]): Seq[Foo] = ???
        |  def f(a: Seq[Bar]): Seq[Bar] = ???
        |
        |  def f1(a: Seq[Seq[Foo]]): Seq[Seq[Foo]] = ???
        |  def f1(a: Seq[Seq[Bar]]): Seq[Seq[Bar]] = ???
        |
        |  def f2(a: Seq[Foo], b: Seq[Bar]): Seq[Foo] = ???
        |  def f2(a: Seq[Bar], b: Seq[Foo]): Seq[Bar] = ???
        |
        |  def f3(a: Seq[Seq[Foo]]): Seq[Seq[Foo]] = ???
        |  def f3(a: Seq[Seq[Bar]]): Seq[Seq[Bar]] = ???
        |
        |  def clash1(a: Seq[Box[Foo]]): Seq[Box[Foo]] = ???
        |  def clash1(a: Seq[Box[Bar]]): Seq[Box[Bar]] = ???
        |
        |  def clash2(a: Seq[Seq[Box[Foo]]]): Seq[Seq[Box[Foo]]] = ???
        |  def clash2(a: Seq[Seq[Box[Bar]]]): Seq[Seq[Box[Bar]]] = ???
        |}
        |""".stripMargin,
      """Error(f,f(_root_.scala.collection.immutable.Seq)_root_.scala.collection.immutable.Seq is already defined in the scope)
        |Error(f,f(_root_.scala.collection.immutable.Seq)_root_.scala.collection.immutable.Seq is already defined in the scope)
        |Error(f1,f1(_root_.scala.collection.immutable.Seq)_root_.scala.collection.immutable.Seq is already defined in the scope)
        |Error(f1,f1(_root_.scala.collection.immutable.Seq)_root_.scala.collection.immutable.Seq is already defined in the scope)
        |Error(f2,f2(_root_.scala.collection.immutable.Seq, _root_.scala.collection.immutable.Seq)_root_.scala.collection.immutable.Seq is already defined in the scope)
        |Error(f2,f2(_root_.scala.collection.immutable.Seq, _root_.scala.collection.immutable.Seq)_root_.scala.collection.immutable.Seq is already defined in the scope)
        |Error(f3,f3(_root_.scala.collection.immutable.Seq)_root_.scala.collection.immutable.Seq is already defined in the scope)
        |Error(f3,f3(_root_.scala.collection.immutable.Seq)_root_.scala.collection.immutable.Seq is already defined in the scope)
        |Error(clash1,clash1(_root_.scala.collection.immutable.Seq)_root_.scala.collection.immutable.Seq is already defined in the scope)
        |Error(clash1,clash1(_root_.scala.collection.immutable.Seq)_root_.scala.collection.immutable.Seq is already defined in the scope)
        |Error(clash2,clash2(_root_.scala.collection.immutable.Seq)_root_.scala.collection.immutable.Seq is already defined in the scope)
        |Error(clash2,clash2(_root_.scala.collection.immutable.Seq)_root_.scala.collection.immutable.Seq is already defined in the scope)
        |""".stripMargin
    )

  def testTypeErasure_WithTypeAlias(): Unit =
    assertErrorsText(
      """class Foo
        |class Bar
        |trait Box[T]
        |
        |object Test {
        |  type MySeq[T] = Seq[T]
        |
        |  def f(a: MySeq[Foo]): MySeq[Foo] = ???
        |  def f(a: MySeq[Bar]): MySeq[Bar] = ???
        |
        |  def f1(a: MySeq[MySeq[Foo]]): MySeq[MySeq[Foo]] = ???
        |  def f1(a: MySeq[MySeq[Bar]]): MySeq[MySeq[Bar]] = ???
        |
        |  def f2(a: MySeq[Foo], b: MySeq[Bar]): MySeq[Foo] = ???
        |  def f2(a: MySeq[Bar], b: MySeq[Foo]): MySeq[Bar] = ???
        |
        |  def f3(a: MySeq[MySeq[Foo]]): MySeq[MySeq[Foo]] = ???
        |  def f3(a: MySeq[MySeq[Bar]]): MySeq[MySeq[Bar]] = ???
        |
        |  def clash1(a: MySeq[Box[Foo]]): MySeq[Box[Foo]] = ???
        |  def clash1(a: MySeq[Box[Bar]]): MySeq[Box[Bar]] = ???
        |
        |  def clash2(a: MySeq[MySeq[Box[Foo]]]): MySeq[MySeq[Box[Foo]]] = ???
        |  def clash2(a: MySeq[MySeq[Box[Bar]]]): MySeq[MySeq[Box[Bar]]] = ???
        |}
        |""".stripMargin,
      """Error(f,f(_root_.scala.collection.immutable.Seq)_root_.scala.collection.immutable.Seq is already defined in the scope)
        |Error(f,f(_root_.scala.collection.immutable.Seq)_root_.scala.collection.immutable.Seq is already defined in the scope)
        |Error(f1,f1(_root_.scala.collection.immutable.Seq)_root_.scala.collection.immutable.Seq is already defined in the scope)
        |Error(f1,f1(_root_.scala.collection.immutable.Seq)_root_.scala.collection.immutable.Seq is already defined in the scope)
        |Error(f2,f2(_root_.scala.collection.immutable.Seq, _root_.scala.collection.immutable.Seq)_root_.scala.collection.immutable.Seq is already defined in the scope)
        |Error(f2,f2(_root_.scala.collection.immutable.Seq, _root_.scala.collection.immutable.Seq)_root_.scala.collection.immutable.Seq is already defined in the scope)
        |Error(f3,f3(_root_.scala.collection.immutable.Seq)_root_.scala.collection.immutable.Seq is already defined in the scope)
        |Error(f3,f3(_root_.scala.collection.immutable.Seq)_root_.scala.collection.immutable.Seq is already defined in the scope)
        |Error(clash1,clash1(_root_.scala.collection.immutable.Seq)_root_.scala.collection.immutable.Seq is already defined in the scope)
        |Error(clash1,clash1(_root_.scala.collection.immutable.Seq)_root_.scala.collection.immutable.Seq is already defined in the scope)
        |Error(clash2,clash2(_root_.scala.collection.immutable.Seq)_root_.scala.collection.immutable.Seq is already defined in the scope)
        |Error(clash2,clash2(_root_.scala.collection.immutable.Seq)_root_.scala.collection.immutable.Seq is already defined in the scope)
        |""".stripMargin
    )

  def testNoTypeErasureForArray(): Unit =
    assertErrorsText(
      """class Foo
        |class Bar
        |trait Box[T]
        |
        |object Test {
        |  def f(a: Array[Foo]): Array[Foo] = ???
        |  def f(a: Array[Bar]): Array[Bar] = ???
        |
        |  def f1(a: Array[Array[Foo]]): Array[Array[Foo]] = ???
        |  def f1(a: Array[Array[Bar]]): Array[Array[Bar]] = ???
        |
        |  def f2(a: Array[Foo], b: Array[Bar]): Array[Foo] = ???
        |  def f2(a: Array[Bar], b: Array[Foo]): Array[Bar] = ???
        |
        |  def f3(a: Array[Array[Foo]]): Array[Array[Foo]] = ???
        |  def f3(a: Array[Array[Bar]]): Array[Array[Bar]] = ???
        |
        |  def clash1(a: Array[Box[Foo]]): Array[Box[Foo]] = ???
        |  def clash1(a: Array[Box[Bar]]): Array[Box[Bar]] = ???
        |
        |  def clash2(a: Array[Array[Box[Foo]]]): Array[Array[Box[Foo]]] = ???
        |  def clash2(a: Array[Array[Box[Bar]]]): Array[Array[Box[Bar]]] = ???
        |}
      """.stripMargin,
      """Error(clash1,clash1(Array[Box])Array[Box] is already defined in the scope)
        |Error(clash1,clash1(Array[Box])Array[Box] is already defined in the scope)
        |Error(clash2,clash2(Array[Array[Box]])Array[Array[Box]] is already defined in the scope)
        |Error(clash2,clash2(Array[Array[Box]])Array[Array[Box]] is already defined in the scope)
        |""".stripMargin
    )

  def testNoTypeErasureForArray_TypeAlias(): Unit =
    assertErrorsText(
      """class Foo
        |class Bar
        |trait Box[T]
        |
        |object Test {
        |  type MyArray[T] = Array[T]
        |
        |  def f(a: MyArray[Foo]): MyArray[Foo] = ???
        |  def f(a: MyArray[Bar]): MyArray[Bar] = ???
        |
        |  def f1(a: MyArray[MyArray[Foo]]): MyArray[MyArray[Foo]] = ???
        |  def f1(a: MyArray[MyArray[Bar]]): MyArray[MyArray[Bar]] = ???
        |
        |  def f2(a: MyArray[Foo], b: MyArray[Bar]): MyArray[Foo] = ???
        |  def f2(a: MyArray[Bar], b: MyArray[Foo]): MyArray[Bar] = ???
        |
        |  def f3(a: MyArray[MyArray[Foo]]): MyArray[MyArray[Foo]] = ???
        |  def f3(a: MyArray[MyArray[Bar]]): MyArray[MyArray[Bar]] = ???
        |
        |  def clash1(a: MyArray[Box[Foo]]): MyArray[Box[Foo]] = ???
        |  def clash1(a: MyArray[Box[Bar]]): MyArray[Box[Bar]] = ???
        |
        |  def clash2(a: MyArray[MyArray[Box[Foo]]]): MyArray[MyArray[Box[Foo]]] = ???
        |  def clash2(a: MyArray[MyArray[Box[Bar]]]): MyArray[MyArray[Box[Bar]]] = ???
        |}
        |""".stripMargin,
      """Error(clash1,clash1(Array[Box])Array[Box] is already defined in the scope)
        |Error(clash1,clash1(Array[Box])Array[Box] is already defined in the scope)
        |Error(clash2,clash2(Array[Array[Box]])Array[Array[Box]] is already defined in the scope)
        |Error(clash2,clash2(Array[Array[Box]])Array[Array[Box]] is already defined in the scope)
        |""".stripMargin
    )

  //see testdata/scalacTests/pos/t8219b.scala
  def testNoErasureInStructuralType(): Unit = assertNoMessages(
    """trait Foo[T]
      |object Test {
      |
      |  type T = {
      |    def foo(f: Foo[Int])
      |    def foo(f: Foo[Boolean])
      |    def foo(f: Array[Int])
      |    def foo(f: Array[AnyRef])
      |    def foo(f: Array[Any])
      |    def foo(f: AnyVal)
      |    def foo(f: Any)
      |    def foo(f: AnyRef)
      |  }
      |}
      """.stripMargin
  )
  //SCL-3137
  def testSCL3137(): Unit = assertNothing(
    errorsFromScalaCode(
      """
        |package a {
        |  class Foo
        |}
        |
        |package b {
        |  class Foo
        |}
        |
        |package c {
        |  trait Bar {
        |    def foo(foo: a.Foo): Unit = ???
        |    def foo(foo: b.Foo): Unit = ???
        |  }
        |}
      """.stripMargin)
  )
}

class ScopeAnnotatorHeavyTest_Scala_3 extends ScopeAnnotatorHeavyTest {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version.isScala3

  override protected def assertNoMessages(@Language("Scala 3") code: String): Unit =
    super.assertNoMessages(code)

  override protected def assertErrorsText(@Language("Scala 3") code: String, messagesConcatenated: String): Unit =
    super.assertErrorsText(code, messagesConcatenated)

  def testExtensionMethodsWithSameName(): Unit = assertNothing(
    errorsFromScala3Code(
      """extension (n: Int)
        |  def mySpecialToString: String = n.toString
        |  def mySpecialMkString(prefix: String, separator: String, postfix: String): String =
        |    List(n).mkString(prefix, separator, postfix)
        |
        |extension (n: Long)
        |  def mySpecialToString: String = n.toString
        |  def mySpecialMkString(prefix: String, separator: String, postfix: String): String =
        |    List(n).mkString(prefix, separator, postfix)
        """.stripMargin)
  )

  def testNoTypeErasureForIArray(): Unit =
    assertErrorsText(
      """class Foo
        |class Bar
        |trait Box[T]
        |
        |object Test {
        |  def f(a: IArray[Foo]): IArray[Foo] = ???
        |  def f(a: IArray[Bar]): IArray[Bar] = ???
        |
        |  def f1(a: IArray[IArray[Foo]]): IArray[IArray[Foo]] = ???
        |  def f1(a: IArray[IArray[Bar]]): IArray[IArray[Bar]] = ???
        |
        |  def f2(a: IArray[Foo], b: IArray[Bar]): IArray[Foo] = ???
        |  def f2(a: IArray[Bar], b: IArray[Foo]): IArray[Bar] = ???
        |
        |  def f3(a: IArray[IArray[Foo]]): IArray[IArray[Foo]] = ???
        |  def f3(a: IArray[IArray[Bar]]): IArray[IArray[Bar]] = ???
        |
        |  def clash1(a: IArray[Box[Foo]]): IArray[Box[Foo]] = ???
        |  def clash1(a: IArray[Box[Bar]]): IArray[Box[Bar]] = ???
        |
        |  def clash2(a: IArray[IArray[Box[Foo]]]): IArray[IArray[Box[Foo]]] = ???
        |  def clash2(a: IArray[IArray[Box[Bar]]]): IArray[IArray[Box[Bar]]] = ???
        |}
        |""".stripMargin,
      """Error(clash1,clash1(IArray[Box])IArray[Box] is already defined in the scope)
        |Error(clash1,clash1(IArray[Box])IArray[Box] is already defined in the scope)
        |Error(clash2,clash2(IArray[IArray[Box]])IArray[IArray[Box]] is already defined in the scope)
        |Error(clash2,clash2(IArray[IArray[Box]])IArray[IArray[Box]] is already defined in the scope)
        |""".stripMargin,
    )

  //TODO: once true opaque types support is implemented (SCL-20887),
  // we need to unmute this test (remove return) we should reconsider SCL-22062 (it should work for custom array type aliases as well)
  def testNoTypeErasureForArray_CustomOpaqueTypeAlias(): Unit = {
    return
    assertErrorsText(
      """class Foo
        |class Bar
        |trait Box[T]
        |
        |opaque type MyArray[T] = Array[T]
        |
        |object Test {
        |  def f(a: MyArray[Foo]): MyArray[Foo] = ???
        |  def f(a: MyArray[Bar]): MyArray[Bar] = ???
        |
        |  def f1(a: MyArray[MyArray[Foo]]): MyArray[MyArray[Foo]] = ???
        |  def f1(a: MyArray[MyArray[Bar]]): MyArray[MyArray[Bar]] = ???
        |
        |  def f2(a: MyArray[Foo], b: MyArray[Bar]): MyArray[Foo] = ???
        |  def f2(a: MyArray[Bar], b: MyArray[Foo]): MyArray[Bar] = ???
        |
        |  def f3(a: MyArray[MyArray[Foo]]): MyArray[MyArray[Foo]] = ???
        |  def f3(a: MyArray[MyArray[Bar]]): MyArray[MyArray[Bar]] = ???
        |
        |  def clash1(a: MyArray[Box[Foo]]): MyArray[Box[Foo]] = ???
        |  def clash1(a: MyArray[Box[Bar]]): MyArray[Box[Bar]] = ???
        |
        |  def clash2(a: MyArray[MyArray[Box[Foo]]]): MyArray[MyArray[Box[Foo]]] = ???
        |  def clash2(a: MyArray[MyArray[Box[Bar]]]): MyArray[MyArray[Box[Bar]]] = ???
        |}
        |""".stripMargin,
      """Error(clash1,clash1(MyArray[Box])MyArray[Box] is already defined in the scope)
        |Error(clash1,clash1(MyArray[Box])MyArray[Box] is already defined in the scope)
        |Error(clash2,clash2(MyArray[MyArray[Box]])MyArray[MyArray[Box]] is already defined in the scope)
        |Error(clash2,clash2(MyArray[MyArray[Box]])MyArray[MyArray[Box]] is already defined in the scope)
        |""".stripMargin,
    )
  }

  //TODO: once true opaque types support is implemented (SCL-20887),
  // we need to unmute this test (remove return) we should reconsider SCL-22062 (it should work for custom array type aliases as well)
  def testNoTypeErasureForArray_CustomOpaqueTypeAlias_Existential(): Unit = {
    return
    assertErrorsText(
      """class Foo
        |class Bar
        |trait Box[T]
        |
        |opaque type MyIArray[+T] = _root_.scala.Array[? <: T]
        |
        |object Test {
        |  def f(a: MyIArray[Foo]): MyIArray[Foo] = ???
        |  def f(a: MyIArray[Bar]): MyIArray[Bar] = ???
        |
        |  def f1(a: MyIArray[MyIArray[Foo]]): MyIArray[MyIArray[Foo]] = ???
        |  def f1(a: MyIArray[MyIArray[Bar]]): MyIArray[MyIArray[Bar]] = ???
        |
        |  def f2(a: MyIArray[Foo], b: MyIArray[Bar]): MyIArray[Foo] = ???
        |  def f2(a: MyIArray[Bar], b: MyIArray[Foo]): MyIArray[Bar] = ???
        |
        |  def f3(a: MyIArray[MyIArray[Foo]]): MyIArray[MyIArray[Foo]] = ???
        |  def f3(a: MyIArray[MyIArray[Bar]]): MyIArray[MyIArray[Bar]] = ???
        |
        |  def clash1(a: MyIArray[Box[Foo]]): MyIArray[Box[Foo]] = ???
        |  def clash1(a: MyIArray[Box[Bar]]): MyIArray[Box[Bar]] = ???
        |
        |  def clash2(a: MyIArray[MyIArray[Box[Foo]]]): MyIArray[MyIArray[Box[Foo]]] = ???
        |  def clash2(a: MyIArray[MyIArray[Box[Bar]]]): MyIArray[MyIArray[Box[Bar]]] = ???
        |}
        |""".stripMargin,
      """Error(clash1,clash1(MyIArray[Box])MyIArray[Box] is already defined in the scope)
        |Error(clash1,clash1(MyIArray[Box])MyIArray[Box] is already defined in the scope)
        |Error(clash2,clash2(MyIArray[MyIArray[Box]])MyIArray[MyIArray[Box]] is already defined in the scope)
        |Error(clash2,clash2(MyIArray[MyIArray[Box]])MyIArray[MyIArray[Box]] is already defined in the scope)
        |""".stripMargin,
    )
  }

  //TODO: once true opaque types support is implemented (SCL-20887),
  // we need to unmute this test (remove return) we should reconsider SCL-22062 (it should work for custom array type aliases as well)
  def testNoTypeErasureForArray_CustomAbstractType_Existential(): Unit = {
    return
    assertErrorsText(
      """class Foo
        |class Bar
        |trait Box[T]
        |
        |type MyIArray[+T] <: _root_.scala.Array[? <: T]
        |
        |object Test {
        |  def f(a: MyIArray[Foo]): MyIArray[Foo] = ???
        |  def f(a: MyIArray[Bar]): MyIArray[Bar] = ???
        |
        |  def f1(a: MyIArray[MyIArray[Foo]]): MyIArray[MyIArray[Foo]] = ???
        |  def f1(a: MyIArray[MyIArray[Bar]]): MyIArray[MyIArray[Bar]] = ???
        |
        |  def f2(a: MyIArray[Foo], b: MyIArray[Bar]): MyIArray[Foo] = ???
        |  def f2(a: MyIArray[Bar], b: MyIArray[Foo]): MyIArray[Bar] = ???
        |
        |  def f3(a: MyIArray[MyIArray[Foo]]): MyIArray[MyIArray[Foo]] = ???
        |  def f3(a: MyIArray[MyIArray[Bar]]): MyIArray[MyIArray[Bar]] = ???
        |
        |  def clash1(a: MyIArray[Box[Foo]]): MyIArray[Box[Foo]] = ???
        |  def clash1(a: MyIArray[Box[Bar]]): MyIArray[Box[Bar]] = ???
        |
        |  def clash2(a: MyIArray[MyIArray[Box[Foo]]]): MyIArray[MyIArray[Box[Foo]]] = ???
        |  def clash2(a: MyIArray[MyIArray[Box[Bar]]]): MyIArray[MyIArray[Box[Bar]]] = ???
        |}
        |""".stripMargin,
      """Error(clash1,clash1(MyIArray[Box])MyIArray[Box] is already defined in the scope)
        |Error(clash1,clash1(MyIArray[Box])MyIArray[Box] is already defined in the scope)
        |Error(clash2,clash2(MyIArray[MyIArray[Box]])MyIArray[MyIArray[Box]] is already defined in the scope)
        |Error(clash2,clash2(MyIArray[MyIArray[Box]])MyIArray[MyIArray[Box]] is already defined in the scope)
        |""".stripMargin
    )
  }
}
