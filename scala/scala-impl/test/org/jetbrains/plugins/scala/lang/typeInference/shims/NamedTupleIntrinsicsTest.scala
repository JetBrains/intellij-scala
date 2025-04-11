package org.jetbrains.plugins.scala.lang.typeInference.shims

import org.jetbrains.plugins.scala.{ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

/**
 * Tests [[org.jetbrains.plugins.scala.lang.psi.types.intrinsics.NamedTupleIntrinsics]]
 */
@Category(Array(classOf[TypecheckerTests]))
class NamedTupleIntrinsicsTest extends TypeIntrinsicsTestBase {
  override def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_7

  //
  //
  // NamedTuple.Size[_]
  //
  //

  def testSize_simple_one(): Unit =
    assertTypeIs("type T = NamedTuple.Size[(int: Int)]", "1")

  def testSize_simple_two(): Unit =
    assertTypeIs("type T = NamedTuple.Size[(int: Int, s: String)]", "2")

  def testSize_aliased(): Unit =
    assertTypeIs(
      """type Tup = (int: Int, s: String)
        |type T = NamedTuple.Size[Tup]
        |""".stripMargin,
      "2"
    )

  def testSize_abstract(): Unit =
    assertTypeIs("type T[X] = NamedTuple.Size[X]", "NamedTuple.Size[X]")

  def testSize_abstract_inner(): Unit =
    assertTypeIs("type T[X] = NamedTuple.Size[(x: X)]", "1")

  def testSize_func(): Unit =
    assertConforms(
      """type F[X] = NamedTuple.Size[X]
        |type T = F[(int: Int, s: String)]
        |""".stripMargin,
      "2"
    )

  //
  //
  // NamedTuple.Elem[_, _]
  //
  //

  def testElem_simple_first(): Unit =
    assertTypeIs("type T = NamedTuple.Elem[(int: Int, str: String), 0]", "Int")

  def testElem_simple_second(): Unit =
    assertTypeIs("type T = NamedTuple.Elem[(int: Int, str: String), 1]", "String")

  def testElem_aliased(): Unit =
    assertTypeIs(
      """type Tup = (int: Int, str: String)
        |type T = NamedTuple.Elem[Tup, 0]
        |""".stripMargin,
      "Int"
    )

  def testElem_abstract_tuple(): Unit =
    assertTypeIs("type T[X] = NamedTuple.Elem[X, 0]", "NamedTuple.Elem[X, 0]")

  def testElem_abstract_index(): Unit =
    assertTypeIs("type T[N] = NamedTuple.Elem[(x: Int, y: String), N]", "NamedTuple.Elem[(x: Int, y: String), N]")

  def testElem_func(): Unit =
    assertConforms(
      """type F[X, N] = NamedTuple.Elem[X, N]
        |type T = F[(int: Int, str: String), 1]
        |""".stripMargin,
      "String"
    )

  def testElem_single_element(): Unit =
    assertTypeIs("type T = NamedTuple.Elem[(x: Boolean), 0]", "Boolean")

  def testElem_complex_types(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Elem[(list: List[Int], opt: Option[String]), 1]",
      "Option[String]"
    )

  //
  //
  // NamedTuple.Head[_]
  //
  //

  def testHead_simple(): Unit =
    assertTypeIs("type T = NamedTuple.Head[(x: Int, y: String)]", "Int")

  def testHead_single_element(): Unit =
    assertTypeIs("type T = NamedTuple.Head[(only: Boolean)]", "Boolean")

  def testHead_aliased(): Unit =
    assertTypeIs(
      """type Tup = (first: Double, second: String, third: Int)
        |type T = NamedTuple.Head[Tup]
        |""".stripMargin,
      "Double"
    )

  def testHead_abstract(): Unit =
    assertTypeIs("type T[X] = NamedTuple.Head[X]", "NamedTuple.Head[X]")

  def testHead_abstract_inner(): Unit =
    assertTypeIs("type T[X] = NamedTuple.Head[(x: X, y: String)]", "X")

  def testHead_func(): Unit =
    assertConforms(
      """type F[X] = NamedTuple.Head[X]
        |type T = F[(num: Int, str: String)]
        |""".stripMargin,
      "Int"
    )

  def testHead_complex_type(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Head[(complex: Map[String, List[Int]], simple: Boolean)]",
      "Map[String, List[Int]]"
    )

  //
  //
  // NamedTuple.Last[_]
  //
  //

  def testLast_simple(): Unit =
    assertTypeIs("type T = NamedTuple.Last[(x: Int, y: String)]", "String")

  def testLast_single_element(): Unit =
    assertTypeIs("type T = NamedTuple.Last[(only: Boolean)]", "Boolean")

  def testLast_three_elements(): Unit =
    assertTypeIs("type T = NamedTuple.Last[(x: Int, y: String, z: Double)]", "Double")

  def testLast_aliased(): Unit =
    assertTypeIs(
      """type Tup = (first: Double, second: String, third: Int)
        |type T = NamedTuple.Last[Tup]
        |""".stripMargin,
      "Int"
    )

  def testLast_abstract(): Unit =
    assertTypeIs("type T[X] = NamedTuple.Last[X]", "NamedTuple.Last[X]")

  def testLast_abstract_inner(): Unit =
    assertTypeIs("type T[X] = NamedTuple.Last[(str: String, x: X)]", "X")

  def testLast_func(): Unit =
    assertConforms(
      """type F[X] = NamedTuple.Last[X]
        |type T = F[(num: Int, str: String, flag: Boolean)]
        |""".stripMargin,
      "Boolean"
    )

  def testLast_complex_type(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Last[(simple: Boolean, complex: Map[String, List[Int]])]",
      "Map[String, List[Int]]"
    )

  //
  //
  // NamedTuple.Tail[_]
  //
  //

  def testTail_simple(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Tail[(x: Int, y: String)]",
      "(y: String)"
    )

  def testTail_three_elements(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Tail[(x: Int, y: String, z: Boolean)]",
      "(y: String, z: Boolean)"
    )

  // todo: not working due to non existence of empty tuple in the current implementation
  //def testTail_single_element(): Unit =
  //  assertTypeIs(
  //    "type T = NamedTuple.Tail[(x: Int)]",
  //    "()"
  //  )

  def testTail_aliased(): Unit =
    assertTypeIs(
      """type Tup = (a: Int, b: String, c: Double)
        |type T = NamedTuple.Tail[Tup]
        |""".stripMargin,
      "(b: String, c: Double)"
    )

  def testTail_abstract(): Unit =
    assertTypeIs("type T[X] = NamedTuple.Tail[X]", "NamedTuple.Tail[X]")

  def testTail_abstract_inner(): Unit =
    assertTypeIs(
      "type T[X] = NamedTuple.Tail[(head: String, mid: X, last: Boolean)]",
      "(mid: X, last: Boolean)"
    )

  def testTail_func(): Unit =
    assertConforms(
      """type F[X] = NamedTuple.Tail[X]
        |type T = F[(first: Int, second: String, third: Boolean)]
        |""".stripMargin,
      "(second: String, third: Boolean)"
    )

  def testTail_complex_types(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Tail[(a: List[Int], b: Map[String, Int], c: Option[Double])]",
      "(b: Map[String, Int], c: Option[Double])"
    )

  def testTail_nested(): Unit =
    assertTypeIs(
      """type First = (x: Int, y: String, z: Boolean)
        |type T = NamedTuple.Tail[NamedTuple.Tail[First]]
        |""".stripMargin,
      "(z: Boolean)"
    )

  //
  //
  // NamedTuple.Init[_]
  //
  //

  def testInit_simple(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Init[(x: Int, y: String)]",
      "(x: Int)"
    )

  def testInit_three_elements(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Init[(x: Int, y: String, z: Boolean)]",
      "(x: Int, y: String)"
    )

  // todo: not working due to non existence of empty tuple in the current implementation
  //def testInit_single_element(): Unit =
  //  assertTypeIs(
  //    "type T = NamedTuple.Init[(x: Int)]",
  //    "()"
  //  )

  def testInit_aliased(): Unit =
    assertTypeIs(
      """type Tup = (a: Int, b: String, c: Double)
        |type T = NamedTuple.Init[Tup]
        |""".stripMargin,
      "(a: Int, b: String)"
    )

  def testInit_abstract(): Unit =
    assertTypeIs("type T[X] = NamedTuple.Init[X]", "NamedTuple.Init[X]")

  def testInit_abstract_inner(): Unit =
    assertTypeIs(
      "type T[X] = NamedTuple.Init[(first: String, mid: X, last: Boolean)]",
      "(first: String, mid: X)"
    )

  def testInit_func(): Unit =
    assertConforms(
      """type F[X] = NamedTuple.Init[X]
        |type T = F[(first: Int, second: String, third: Boolean)]
        |""".stripMargin,
      "(first: Int, second: String)"
    )

  def testInit_complex_types(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Init[(a: List[Int], b: Map[String, Int], c: Option[Double])]",
      "(a: List[Int], b: Map[String, Int])"
    )

  def testInit_nested(): Unit =
    assertTypeIs(
      """type First = (x: Int, y: String, z: Boolean)
        |type T = NamedTuple.Init[NamedTuple.Init[First]]
        |""".stripMargin,
      "(x: Int)"
    )

  //
  //
  // NamedTuple.Take[_, _]
  //
  //

  def testTake_simple(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Take[(x: Int, y: String, z: Boolean), 2]",
      "(x: Int, y: String)"
    )

  def testTake_single(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Take[(x: Int, y: String, z: Boolean), 1]",
      "(x: Int)"
    )

  def testTake_all(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Take[(x: Int, y: String), 2]",
      "(x: Int, y: String)"
    )

  def testTake_aliased(): Unit =
    assertTypeIs(
      """type Tup = (a: Int, b: String, c: Double)
        |type T = NamedTuple.Take[Tup, 2]
        |""".stripMargin,
      "(a: Int, b: String)"
    )

  def testTake_abstract_tuple(): Unit =
    assertTypeIs(
      "type T[X] = NamedTuple.Take[X, 2]",
      "NamedTuple.Take[X, 2]"
    )

  def testTake_abstract_n(): Unit =
    assertTypeIs(
      "type T[N] = NamedTuple.Take[(x: Int, y: String), N]",
      "NamedTuple.Take[(x: Int, y: String), N]"
    )

  def testTake_func(): Unit =
    assertConforms(
      """type F[X, N] = NamedTuple.Take[X, N]
        |type T = F[(first: Int, second: String, third: Boolean), 2]
        |""".stripMargin,
      "(first: Int, second: String)"
    )

  def testTake_complex_types(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Take[(a: List[Int], b: Map[String, Int], c: Option[Double]), 2]",
      "(a: List[Int], b: Map[String, Int])"
    )

  def testTake_nested(): Unit =
    assertTypeIs(
      """type Tup = (w: Int, x: String, y: Boolean, z: Double)
        |type T = NamedTuple.Take[NamedTuple.Take[Tup, 3], 2]
        |""".stripMargin,
      "(w: Int, x: String)"
    )

  def testTake_with_type_param(): Unit =
    assertTypeIs(
      """type Container[A] = (first: A, second: String, third: Boolean)
        |type T = NamedTuple.Take[Container[Int], 2]
        |""".stripMargin,
      "(first: Int, second: String)"
    )

  //
  //
  // NamedTuple.Drop[_, _]
  //
  //

  def testDrop_simple(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Drop[(x: Int, y: String, z: Boolean), 1]",
      "(y: String, z: Boolean)"
    )

  def testDrop_two(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Drop[(x: Int, y: String, z: Boolean), 2]",
      "(z: Boolean)"
    )

  def testDrop_aliased(): Unit =
    assertTypeIs(
      """type Tup = (a: Int, b: String, c: Double)
        |type T = NamedTuple.Drop[Tup, 2]
        |""".stripMargin,
      "(c: Double)"
    )

  def testDrop_abstract_tuple(): Unit =
    assertTypeIs(
      "type T[X] = NamedTuple.Drop[X, 1]",
      "NamedTuple.Drop[X, 1]"
    )

  def testDrop_abstract_n(): Unit =
    assertTypeIs(
      "type T[N] = NamedTuple.Drop[(x: Int, y: String), N]",
      "NamedTuple.Drop[(x: Int, y: String), N]"
    )

  def testDrop_complex_types(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Drop[(a: List[Int], b: Map[String, Int], c: Option[Double]), 1]",
      "(b: Map[String, Int], c: Option[Double])"
    )

  //
  //
  // NamedTuple.Split[_, _]
  //
  //

  def testSplit_simple(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Split[(x: Int, y: String, z: Boolean), 1]",
      "((x: Int), (y: String, z: Boolean))"
    )

  def testSplit_middle(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Split[(x: Int, y: String, z: Boolean), 2]",
      "((x: Int, y: String), (z: Boolean))"
    )

  def testSplit_aliased(): Unit =
    assertTypeIs(
      """type Tup = (a: Int, b: String, c: Double)
        |type T = NamedTuple.Split[Tup, 1]
        |""".stripMargin,
      "((a: Int), (b: String, c: Double))"
    )

  def testSplit_abstract_tuple(): Unit =
    assertTypeIs(
      "type T[X] = NamedTuple.Split[X, 1]",
      "(NamedTuple.Take[X, 1], NamedTuple.Drop[X, 1])" // should be NamedTuple.Split[X, 1]?
    )

  def testSplit_abstract_n(): Unit =
    assertTypeIs(
      "type T[N] = NamedTuple.Split[(x: Int, y: String), N]",
      "(NamedTuple.Take[(x: Int, y: String), N], NamedTuple.Drop[(x: Int, y: String), N])" // should be NamedTuple.Split[(x: Int, y: String), N]?
    )

  def testSplit_complex_types(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Split[(a: List[Int], b: Map[String, Int], c: Option[Double]), 2]",
      "((a: List[Int], b: Map[String, Int]), (c: Option[Double]))"
    )

  //
  //
  // NamedTuple.Concat[_, _]
  //
  //

  def testConcat_simple(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Concat[(x: Int), (y: String)]",
      "(x: Int, y: String)"
    )

  def testConcat_multiple(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Concat[(a: Int, b: String), (c: Boolean, d: Double)]",
      "(a: Int, b: String, c: Boolean, d: Double)"
    )

  def testConcat_aliased(): Unit =
    assertTypeIs(
      """type First = (a: Int, b: String)
        |type Second = (c: Boolean, d: Double)
        |type T = NamedTuple.Concat[First, Second]
        |""".stripMargin,
      "(a: Int, b: String, c: Boolean, d: Double)"
    )

  def testConcat_abstract_first(): Unit =
    assertTypeIs(
      "type T[X] = NamedTuple.Concat[X, (y: String, z: Boolean)]",
      "NamedTuple.Concat[X, (y: String, z: Boolean)]"
    )

  def testConcat_abstract_second(): Unit =
    assertTypeIs(
      "type T[X] = NamedTuple.Concat[(x: Int, y: String), X]",
      "NamedTuple.Concat[(x: Int, y: String), X]"
    )

  def testConcat_complex_types(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Concat[(a: List[Int]), (b: Map[String, Int], c: Option[Double])]",
      "(a: List[Int], b: Map[String, Int], c: Option[Double])"
    )

  def testConcat_nested(): Unit =
    assertTypeIs(
      """type A = (x: Int, y: String)
        |type B = (z: Boolean)
        |type C = (w: Double)
        |type T = NamedTuple.Concat[NamedTuple.Concat[A, B], C]
        |""".stripMargin,
      "(x: Int, y: String, z: Boolean, w: Double)"
    )

  //
  //
  // NamedTuple.Map[_, _]
  //
  //

  def testMap_simple(): Unit =
    assertTypeIs(
      """type F[X] = List[X]
        |type T = NamedTuple.Map[(x: Int, y: String), F]
        |""".stripMargin,
      "(x: List[Int], y: List[String])"
    )

  //def testMap_identity(): Unit =
  //  assertTypeIs(
  //    """type Id[X] = X
  //      |type T = NamedTuple.Map[(x: Int, y: String), Id]
  //      |""".stripMargin,
  //    "(x: Int, y: String)"
  //  )

  def testMap_option(): Unit =
    assertTypeIs(
      """type T = NamedTuple.Map[(x: Int, y: String, z: Boolean), Option]
        |""".stripMargin,
      "(x: Option[Int], y: Option[String], z: Option[Boolean])"
    )

  // TODO: currently it's (a: Either[String, ?][List[Int]], b: Either[String, ?][Map[String, Boolean]]) which is wrong
  //def testMap_complex_types(): Unit =
  //  assertTypeIs(
  //    """type F[X] = Either[String, X]
  //      |type T = NamedTuple.Map[(a: List[Int], b: Map[String, Boolean]), F]
  //      |""".stripMargin,
  //    "(a: Either[String, List[Int]], b: Either[String, Map[String, Boolean]])"
  //  )

  def testMap_abstract_tuple(): Unit =
    assertTypeIs(
      """type F[X] = List[X]
        |type T[Tup] = NamedTuple.Map[Tup, F]
        |""".stripMargin,
      "NamedTuple.Map[Tup, F]"
    )

  def testMap_nested(): Unit =
    assertTypeIs(
      """type F[X] = List[X]
        |type G[X] = Option[X]
        |type T = NamedTuple.Map[NamedTuple.Map[(x: Int, y: String), F], G]
        |""".stripMargin,
      "(x: Option[List[Int]], y: Option[List[String]])"
    )

  //
  //
  // NamedTuple.Reverse[_]
  //
  //

  def testReverse_two_elements(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Reverse[(x: Int, y: String)]",
      "(y: String, x: Int)"
    )

  def testReverse_three_elements(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Reverse[(x: Int, y: String, z: Boolean)]",
      "(z: Boolean, y: String, x: Int)"
    )

  def testReverse_aliased(): Unit =
    assertTypeIs(
      """type Tup = (a: Int, b: String, c: Double)
        |type T = NamedTuple.Reverse[Tup]
        |""".stripMargin,
      "(c: Double, b: String, a: Int)"
    )

  def testReverse_abstract(): Unit =
    assertTypeIs(
      "type T[X] = NamedTuple.Reverse[X]",
      "NamedTuple.Reverse[X]"
    )

  def testReverse_complex_types(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Reverse[(a: List[Int], b: Map[String, Int], c: Option[Double])]",
      "(c: Option[Double], b: Map[String, Int], a: List[Int])"
    )

  def testReverse_nested(): Unit =
    assertTypeIs(
      """type Tup = (x: Int, y: String, z: Boolean)
        |type T = NamedTuple.Reverse[NamedTuple.Reverse[Tup]]
        |""".stripMargin,
      "(x: Int, y: String, z: Boolean)"
    )

  //
  //
  // NamedTuple.Zip[_, _]
  //
  //

  def testZip_simple(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Zip[(x: Int, y: String), (x: Boolean, y: Double)]",
      "(x: (Int, Boolean), y: (String, Double))"
    )

  def testZip_aliased(): Unit =
    assertTypeIs(
      """type First = (x: Int, y: String)
        |type Second = (x: Boolean, y: Double)
        |type T = NamedTuple.Zip[First, Second]
        |""".stripMargin,
      "(x: (Int, Boolean), y: (String, Double))"
    )

  def testZip_abstract_first(): Unit =
    assertTypeIs(
      "type T[X] = NamedTuple.Zip[X, (a: Boolean, b: Double)]",
      "NamedTuple.Zip[X, (a: Boolean, b: Double)]"
    )

  def testZip_abstract_second(): Unit =
    assertTypeIs(
      "type T[X] = NamedTuple.Zip[(x: Int, y: String), X]",
      "NamedTuple.Zip[(x: Int, y: String), X]"
    )

  def testZip_complex_types(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Zip[(x: List[Int], y: Option[String]), (x: Set[Double], y: Map[String, Boolean])]",
      "(x: (List[Int], Set[Double]), y: (Option[String], Map[String, Boolean]))"
    )

  def testZip_nested(): Unit =
    assertTypeIs(
      """type A = (x: Int, y: String)
        |type B = (x: Boolean, y: Double)
        |type C = (x: Char, y: Long)
        |type T = NamedTuple.Zip[NamedTuple.Zip[A, B], C]
        |""".stripMargin,
      "(x: ((Int, Boolean), Char), y: ((String, Double), Long))"
    )

  //
  //
  // NamedTuple.From[_]
  //
  //

  def testFrom_namedTuple(): Unit =
    assertTypeIs(
      """type Original = (x: Int, y: String)
        |type T = NamedTuple.From[Original]
        |""".stripMargin,
      "(x: Int, y: String)"
    )

  def testFrom_regularTuple(): Unit =
    assertTypeIs(
      "type T = NamedTuple.From[(Int, String)]",
      "(Int, String)"
    )

  def testFrom_caseClass(): Unit =
    assertTypeIs(
      """case class Person(name: String, age: Int)
        |type T = NamedTuple.From[Person]
        |""".stripMargin,
      "(name: String, age: Int)"
    )

  def testFrom_generic_caseClass(): Unit =
    assertTypeIs(
      """case class Box[A](value: A, label: String)
        |type T = NamedTuple.From[Box[Int]]
        |""".stripMargin,
      "(value: Int, label: String)"
    )

  def testFrom_abstract(): Unit =
    assertTypeIs(
      "type T[X] = NamedTuple.From[X]",
      "NamedTuple.From[X]"
    )

  def testFrom_complex_caseClass(): Unit =
    assertTypeIs(
      """case class Container(list: List[Int], map: Map[String, Boolean])
        |type T = NamedTuple.From[Container]
        |""".stripMargin,
      "(list: List[Int], map: Map[String, Boolean])"
    )

  //
  //
  // NamedTuple.Names[_]
  //
  //

  def testNames_simple(): Unit =
    assertTypeIs(
      "type T = NamedTuple.Names[(x: Int, y: String, z: Boolean)]",
      raw"""("x", "y", "z")"""
    )

  def testNames_aliased(): Unit =
    assertTypeIs(
      """type Tup = (a: Int, b: String, c: Double)
        |type T = NamedTuple.Names[Tup]
        |""".stripMargin,
      raw"""("a", "b", "c")"""
    )

  def testNames_abstract(): Unit =
    assertTypeIs(
      "type T[X] = NamedTuple.Names[X]",
      "NamedTuple.Names[X]"
    )

  //
  //
  // NamedTuple.DropNames[_]
  //
  //

  def testDropNames_simple(): Unit =
    assertTypeIs(
      "type T = NamedTuple.DropNames[(x: Int, y: String, z: Boolean)]",
      "(Int, String, Boolean)"
    )

  def testDropNames_aliased(): Unit =
    assertTypeIs(
      """type Tup = (x: Int, y: String, z: Boolean)
        |type T = NamedTuple.DropNames[Tup]
        |""".stripMargin,
      "(Int, String, Boolean)"
    )

  def testDropNames_abstract(): Unit =
    assertTypeIs(
      "type T[X] = NamedTuple.DropNames[X]",
      "NamedTuple.DropNames[X]"
    )
}
