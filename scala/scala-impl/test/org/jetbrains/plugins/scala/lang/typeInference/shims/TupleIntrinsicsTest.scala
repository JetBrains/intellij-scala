package org.jetbrains.plugins.scala.lang.typeInference.shims

import org.jetbrains.plugins.scala.{ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category


/**
 * Tests [[org.jetbrains.plugins.scala.lang.psi.types.intrinsics.TupleIntrinsics]]
 */
@Category(Array(classOf[TypecheckerTests]))
class TupleIntrinsicsTest extends TypeIntrinsicsTestBase {
  override def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_3_7

  override def transformCode(code: String): String =
    s"""import scala.Tuple.++
       |
       |$code
       |""".stripMargin

  //
  //
  // Tuple.Append[_]
  //
  //

  def testAppend_simple(): Unit =
    assertTypeIs(
      "type T = Tuple.Append[(Int, String), Int]",
      "(Int, String, Int)"
    )

  def testAppend_nested(): Unit =
    assertTypeIs(
      "type T = Tuple.Append[Tuple.Append[(Int, String), Int], (Boolean, Float)]",
      "(Int, String, Int, (Boolean, Float))"
    )

  def testAppend_aliased(): Unit =
    assertTypeIs(
      """type Tup = (Int, String)
        |type T = Tuple.Append[Tup, Int]
        |""".stripMargin,
      "(Int, String, Int)"
    )

  def testAppend_abstract_tup(): Unit =
    assertTypeIs(
      "type T[X] = Tuple.Append[X, Int]",
      "Tuple.Append[X, Int]"
    )

  def testAppend_abstract_elem(): Unit =
    assertTypeIs(
      "type T[X] = Tuple.Append[(Int, String), X]",
      "(Int, String, X)"
    )

  //
  //
  // Tuple.Head[_]
  //
  //

  def testHead_simple(): Unit =
    assertTypeIs("type T = Tuple.Head[(Int, String)]", "Int")

  def testHead_single_element(): Unit =
    assertTypeIs("type T = Tuple.Head[Tuple1[Boolean]]", "Boolean")

  def testHead_aliased(): Unit =
    assertTypeIs(
      """type Tup = (Double, String, Int)
        |type T = Tuple.Head[Tup]
        |""".stripMargin,
      "Double"
    )

  def testHead_abstract(): Unit =
    assertTypeIs("type T[X] = Tuple.Head[X]", "Tuple.Head[X]")

  def testHead_abstract_inner(): Unit =
    assertTypeIs("type T[X] = Tuple.Head[(X, String)]", "X")

  def testHead_func(): Unit =
    assertConforms(
      """type F[X] = Tuple.Head[X]
        |type T = F[(Int, String)]
        |""".stripMargin,
      "Int"
    )

  def testHead_complex_type(): Unit =
    assertTypeIs(
      "type T = Tuple.Head[(Map[String, List[Int]], Boolean)]",
      "Map[String, List[Int]]"
    )

  def testHead_with_rest(): Unit =
    assertTypeIs(
      "type T = Tuple.Head[Int *: NonEmptyTuple]",
      "Int"
    )

  def testHead_only_rest(): Unit =
    assertTypeIs(
      "type T = Tuple.Head[NonEmptyTuple]",
      "Tuple.Head[NonEmptyTuple]"
    )

  //
  //
  // Tuple.Init[_]
  //
  //

  def testInit_simple(): Unit =
    assertTypeIs(
      "type T = Tuple.Init[(Int, String)]",
      "Tuple1[Int]"
    )

  def testInit_three_elements(): Unit =
    assertTypeIs(
      "type T = Tuple.Init[(Int, String, Boolean)]",
      "(Int, String)"
    )

  // todo: not working due to non existence of empty tuple in the current implementation
  //def testInit_single_element(): Unit =
  //  assertTypeIs(
  //    "type T = Tuple.Init[(Int)]",
  //    "()"
  //  )

  def testInit_aliased(): Unit =
    assertTypeIs(
      """type Tup = (Int, String, Double)
        |type T = Tuple.Init[Tup]
        |""".stripMargin,
      "(Int, String)"
    )

  def testInit_abstract(): Unit =
    assertTypeIs("type T[X] = Tuple.Init[X]", "Tuple.Init[X]")

  def testInit_abstract_inner(): Unit =
    assertTypeIs(
      "type T[X] = Tuple.Init[(String, X, Boolean)]",
      "(String, X)"
    )

  def testInit_func(): Unit =
    assertConforms(
      """type F[X] = Tuple.Init[X]
        |type T = F[(Int, String, Boolean)]
        |""".stripMargin,
      "(Int, String)"
    )

  def testInit_nested(): Unit =
    assertTypeIs(
      """type First = (Int, String, Boolean)
        |type T = Tuple.Init[Tuple.Init[First]]
        |""".stripMargin,
      "Tuple1[Int]"
    )

  //
  //
  // Tuple.Tail[_]
  //
  //

  def testTail_simple(): Unit =
    assertTypeIs(
      "type T = Tuple.Tail[(Int, String)]",
      "Tuple1[String]"
    )

  def testTail_three_elements(): Unit =
    assertTypeIs(
      "type T = Tuple.Tail[(Int, String, Boolean)]",
      "(String, Boolean)"
    )

  // todo: not working due to non existence of empty tuple in the current implementation
  //def testTail_single_element(): Unit =
  //  assertTypeIs(
  //    "type T = Tuple.Tail[(x: Int)]",
  //    "()"
  //  )

  def testTail_aliased(): Unit =
    assertTypeIs(
      """type Tup = (Int, String, Double)
        |type T = Tuple.Tail[Tup]
        |""".stripMargin,
      "(String, Double)"
    )

  def testTail_abstract(): Unit =
    assertTypeIs("type T[X] = Tuple.Tail[X]", "Tuple.Tail[X]")

  def testTail_abstract_inner(): Unit =
    assertTypeIs(
      "type T[X] = Tuple.Tail[(String, X, Boolean)]",
      "(X, Boolean)"
    )

  def testTail_func(): Unit =
    assertConforms(
      """type F[X] = Tuple.Tail[X]
        |type T = F[(Int, String, Boolean)]
        |""".stripMargin,
      "(String, Boolean)"
    )

  def testTail_complex_types(): Unit =
    assertTypeIs(
      "type T = Tuple.Tail[(List[Int], Map[String, Int], Option[Double])]",
      "(Map[String, Int], Option[Double])"
    )

  def testTail_nested(): Unit =
    assertTypeIs(
      """type First = (Int, String, Boolean)
        |type T = Tuple.Tail[Tuple.Tail[First]]
        |""".stripMargin,
      "Tuple1[Boolean]"
    )

  def testTail_rest(): Unit =
    assertTypeIs(
      "type T = Tuple.Tail[(Int, String) ++ NonEmptyTuple]",
      "String *: NonEmptyTuple"
    )

  def testTailOnEmpty(): Unit =
    assertTypeIs(
      "type T = Tuple.Tail[EmptyTuple]",
      "Tuple.Tail[EmptyTuple]"
    )

  //
  //
  // Tuple.Last[_]
  //
  //

  def testLast_simple(): Unit =
    assertTypeIs("type T = Tuple.Last[(Int, String)]", "String")

  def testLast_single_element(): Unit =
    assertTypeIs("type T = Tuple.Last[Tuple1[Boolean]]", "Boolean")

  def testLast_three_elements(): Unit =
    assertTypeIs("type T = Tuple.Last[(Int, String, Double)]", "Double")

  def testLast_aliased(): Unit =
    assertTypeIs(
      """type Tup = (Double, String, Int)
        |type T = Tuple.Last[Tup]
        |""".stripMargin,
      "Int"
    )

  def testLast_abstract(): Unit =
    assertTypeIs("type T[X] = Tuple.Last[X]", "Tuple.Last[X]")

  def testLast_abstract_inner(): Unit =
    assertTypeIs("type T[X] = Tuple.Last[(String, X)]", "X")

  def testLast_func(): Unit =
    assertConforms(
      """type F[X] = Tuple.Last[X]
        |type T = F[(Int, String, Boolean)]
        |""".stripMargin,
      "Boolean"
    )

  def testLast_complex_type(): Unit =
    assertTypeIs(
      "type T = Tuple.Last[(Boolean, Map[String, List[Int]])]",
      "Map[String, List[Int]]"
    )

  //
  //
  // Tuple.Concat[_, _]
  //
  //

  def testConcat_simple(): Unit =
    assertTypeIs(
      "type T = Tuple.Concat[Tuple1[Int], Tuple1[String]]",
      "(Int, String)"
    )

  def testConcat_multiple(): Unit =
    assertTypeIs(
      "type T = Tuple.Concat[(Int, String), (Boolean, Double)]",
      "(Int, String, Boolean, Double)"
    )

  def testConcat_aliased(): Unit =
    assertTypeIs(
      """type First = (Int, String)
        |type Second = (Boolean, Double)
        |type T = Tuple.Concat[First, Second]
        |""".stripMargin,
      "(Int, String, Boolean, Double)"
    )

  def testConcat_abstract_first(): Unit =
    assertTypeIs(
      "type T[X] = Tuple.Concat[X, (String, Boolean)]",
      "Tuple.Concat[X, (String, Boolean)]"
    )

  def testConcat_abstract_second(): Unit =
    assertTypeIs(
      "type T[X] = Tuple.Concat[(Int, String), X]",
      "Int *: String *: X"
    )

  def testConcat_complex_types(): Unit =
    assertTypeIs(
      "type T = Tuple.Concat[Tuple1[List[Int]], (Map[String, Int], Option[Double])]",
      "(List[Int], Map[String, Int], Option[Double])"
    )

  def testConcat_nested(): Unit =
    assertTypeIs(
      """type A = (Int, String)
        |type B = Tuple1[Boolean]
        |type C = Tuple1[Double]
        |type T = Tuple.Concat[Tuple.Concat[A, B], C]
        |""".stripMargin,
      "(Int, String, Boolean, Double)"
    )

  def testConcat_with_rest(): Unit =
    assertTypeIs(
      "type T = Tuple.Concat[(Int, String), NonEmptyTuple]",
      "Int *: String *: NonEmptyTuple"
    )

  def testConcat_with_rest2(): Unit =
    assertTypeIs(
      "type T = (Int, String) ++ Tuple",
      "Int *: String *: Tuple"
    )

  def testConcat_with_rest_in_left(): Unit =
    assertTypeIs(
      "type T = (Int *: Tuple) ++ (1, 2)",
      "Int *: Tuple ++ (1, 2)"
    )
  //
  //
  // Tuple.Elem[_, _]
  //
  //

  def testElem_simple_first(): Unit =
    assertTypeIs("type T = Tuple.Elem[(Int, String), 0]", "Int")

  def testElem_simple_second(): Unit =
    assertTypeIs("type T = Tuple.Elem[(Int, String), 1]", "String")

  def testElem_aliased(): Unit =
    assertTypeIs(
      """type Tup = (Int, String)
        |type T = Tuple.Elem[Tup, 0]
        |""".stripMargin,
      "Int"
    )

  def testElem_abstract_tuple(): Unit =
    assertTypeIs(
      "type T[X] = Tuple.Elem[X, 0]",
      "Tuple.Elem[X, 0]"
    )

  def testElem_abstract_index(): Unit =
    assertTypeIs(
      "type T[N] = Tuple.Elem[(Int, String), N]",
      "Tuple.Elem[(Int, String), N]"
    )

  def testElem_func(): Unit =
    assertConforms(
      """type F[X, N] = Tuple.Elem[X, N]
        |type T = F[(Int, String), 1]
        |""".stripMargin,
      "String"
    )

  def testElem_single_element(): Unit =
    assertTypeIs(
      "type T = Tuple.Elem[Tuple1[Boolean], 0]",
      "Boolean"
    )

  def testElem_complex_types(): Unit =
    assertTypeIs(
      "type T = Tuple.Elem[(List[Int], Option[String]), 1]",
      "Option[String]"
    )

  def testElem_elem_is_before_rest(): Unit =
    assertTypeIs(
      "type T = Tuple.Elem[(Int, Boolean) ++ NonEmptyTuple, 1]",
      "Boolean"
    )

  def testElem_elem_is_in_rest(): Unit =
    assertTypeIs(
      "type T = Tuple.Elem[(Int, Boolean) ++ NonEmptyTuple, 2]",
      "Tuple.Elem[Int *: Boolean *: NonEmptyTuple, 2]"
    )

  //
  //
  // Tuple.Size[_]
  //
  //

  def testSize_simple_one(): Unit =
    assertTypeIs("type T = Tuple.Size[Tuple1[Int]]", "1")

  def testSize_simple_two(): Unit =
    assertTypeIs("type T = Tuple.Size[(Int, String)]", "2")

  def testSize_aliased(): Unit =
    assertTypeIs(
      """type Tup = (Int, String)
        |type T = Tuple.Size[Tup]
        |""".stripMargin,
      "2"
    )

  def testSize_abstract(): Unit =
    assertTypeIs("type T[X] = Tuple.Size[X]", "Tuple.Size[X]")

  def testSize_abstract_inner(): Unit =
    assertTypeIs("type T[X] = Tuple.Size[(X, Int)]", "2")

  def testSize_func(): Unit =
    assertConforms(
      """type F[X] = Tuple.Size[X]
        |type T = F[(Int, String)]
        |""".stripMargin,
      "2"
    )

  //
  //
  // Tuple.Fold[_, _, _]
  //
  //

  def testFold_simple(): Unit =
    assertTypeIs(
      """type F = [X, Y] =>> List[Y]
        |type T = Tuple.Fold[(Int, String), Boolean, F]
        |""".stripMargin,
      "F[Int, F[String, Boolean]]"
    )

  //
  //
  // Tuple.Map[_, _]
  //
  //

  def testMap_simple(): Unit =
    assertTypeIs(
      """type F[X] = List[X]
        |type T = Tuple.Map[(Int, String), F]
        |""".stripMargin,
      "(F[Int], F[String])"
    )

  //def testMap_identity(): Unit =
  //  assertTypeIs(
  //    """type Id[X] = X
  //      |type T = Tuple.Map[(Int, String), Id]
  //      |""".stripMargin,
  //    "(Id[Int], Id[String])"
  //  )

  def testMap_option(): Unit =
    assertTypeIs(
      """type T = Tuple.Map[(Int, String, Boolean), Option]
        |""".stripMargin,
      "(Option[Int], Option[String], Option[Boolean])"
    )

  def testMap_complex_types(): Unit =
    assertTypeIs(
      """type F[X] = Either[String, X]
        |type T = Tuple.Map[(List[Int], Map[String, Boolean]), F]
        |""".stripMargin,
      "(F[List[Int]], F[Map[String, Boolean]])"
    )

  def testMap_abstract_tuple(): Unit =
    assertTypeIs(
      """type F[X] = List[X]
        |type T[Tup] = Tuple.Map[Tup, F]
        |""".stripMargin,
      "Tuple.Map[Tup, F]"
    )

  def testMap_nested(): Unit =
    assertTypeIs(
      """type F[X] = List[X]
        |type G[X] = Option[X]
        |type T = Tuple.Map[Tuple.Map[(Int, String), F], G]
        |""".stripMargin,
      "(G[List[Int]], G[List[String]])"
    )

  //
  //
  // todo: Tuple.FlatMap[_, _]
  //
  //
  //def testFlatMap_simple(): Unit =
  //  assertTypeIs(
  //    """type F[X] = Tuple1[List[X]]
  //      |type T = Tuple.FlatMap[(Int, String), F]
  //      |""".stripMargin,
  //    "(List[Int], List[String])"
  //  )

  //
  //
  // todo: Tuple.Filter[_, _]
  //
  //

  //
  //
  // Tuple.Zip[_, _]
  //
  //

  def testZip_simple(): Unit =
    assertTypeIs(
      "type T = Tuple.Zip[(Int, String), (Boolean, Double)]",
      "((Int, Boolean), (String, Double))"
    )

  def testZip_aliased(): Unit =
    assertTypeIs(
      """type First = (Int, String)
        |type Second = (Boolean, Double)
        |type T = Tuple.Zip[First, Second]
        |""".stripMargin,
      "((Int, Boolean), (String, Double))"
    )

  def testZip_abstract_first(): Unit =
    assertTypeIs(
      "type T[X] = Tuple.Zip[X, (Boolean, Double)]",
      "Tuple.Zip[X, (Boolean, Double)]"
    )

  def testZip_abstract_second(): Unit =
    assertTypeIs(
      "type T[X] = Tuple.Zip[(Int, String), X]",
      "Tuple.Zip[(Int, String), X]"
    )

  def testZip_complex_types(): Unit =
    assertTypeIs(
      "type T = Tuple.Zip[(List[Int], Option[String]), (Set[Double], Map[String, Boolean])]",
      "((List[Int], Set[Double]), (Option[String], Map[String, Boolean]))"
    )

  def testZip_nested(): Unit =
    assertTypeIs(
      """type A = (Int, String)
        |type B = (Boolean, Double)
        |type C = (Char, Long)
        |type T = Tuple.Zip[Tuple.Zip[A, B], C]
        |""".stripMargin,
      "(((Int, Boolean), Char), ((String, Double), Long))"
    )

  //
  //
  // todo: Tuple.InverseMap[_, _]
  //
  //

  //
  //
  // todo: Tuple.IsMappedBy
  //
  //

  //
  //
  // Tuple.Reverse[_]
  //
  //

  def testReverse_two_elements(): Unit =
    assertTypeIs(
      "type T = Tuple.Reverse[(Int, String)]",
      "(String, Int)"
    )

  def testReverse_three_elements(): Unit =
    assertTypeIs(
      "type T = Tuple.Reverse[(Int, String, Boolean)]",
      "(Boolean, String, Int)"
    )

  def testReverse_aliased(): Unit =
    assertTypeIs(
      """type Tup = (Int, String, Double)
        |type T = Tuple.Reverse[Tup]
        |""".stripMargin,
      "(Double, String, Int)"
    )

  def testReverse_abstract(): Unit =
    assertTypeIs(
      "type T[X] = Tuple.Reverse[X]",
      "Tuple.Reverse[X]"
    )

  def testReverse_complex_types(): Unit =
    assertTypeIs(
      "type T = Tuple.Reverse[(List[Int], Map[String, Int], Option[Double])]",
      "(Option[Double], Map[String, Int], List[Int])"
    )

  def testReverse_nested(): Unit =
    assertTypeIs(
      """type Tup = (Int, String, Boolean)
        |type T = Tuple.Reverse[Tuple.Reverse[Tup]]
        |""".stripMargin,
      "(Int, String, Boolean)"
    )

  //
  //
  // Tuple.Take[_, _]
  //
  //

  def testTake_simple(): Unit =
    assertTypeIs(
      "type T = Tuple.Take[(Int, String, Boolean), 2]",
      "(Int, String)"
    )

  def testTake_single(): Unit =
    assertTypeIs(
      "type T = Tuple.Take[(Int, String, Boolean), 1]",
      "Tuple1[Int]"
    )

  def testTake_all(): Unit =
    assertTypeIs(
      "type T = Tuple.Take[(Int, String), 2]",
      "(Int, String)"
    )

  def testTake_aliased(): Unit =
    assertTypeIs(
      """type Tup = (Int, String, Double)
        |type T = Tuple.Take[Tup, 2]
        |""".stripMargin,
      "(Int, String)"
    )

  def testTake_abstract_tuple(): Unit =
    assertTypeIs(
      "type T[X] = Tuple.Take[X, 2]",
      "Tuple.Take[X, 2]"
    )

  def testTake_abstract_n(): Unit =
    assertTypeIs(
      "type T[N] = Tuple.Take[(Int, String), N]",
      "Tuple.Take[(Int, String), N]"
    )

  def testTake_func(): Unit =
    assertConforms(
      """type F[X, N] = Tuple.Take[X, N]
        |type T = F[(Int, String, Boolean), 2]
        |""".stripMargin,
      "(Int, String)"
    )

  def testTake_complex_types(): Unit =
    assertTypeIs(
      "type T = Tuple.Take[(List[Int], Map[String, Int], Option[Double]), 2]",
      "(List[Int], Map[String, Int])"
    )

  def testTake_nested(): Unit =
    assertTypeIs(
      """type Tup = (Int, String, Boolean, Double)
        |type T = Tuple.Take[Tuple.Take[Tup, 2], 3]
        |""".stripMargin,
      "(Int, String)"
    )

  def testTake_with_type_param(): Unit =
    assertTypeIs(
      """type Container[A] = (A, String, Boolean)
        |type T = Tuple.Take[Container[Int], 2]
        |""".stripMargin,
      "(Int, String)"
    )

  def testTake_before_rest(): Unit =
    assertTypeIs(
      "type T = Tuple.Take[(Int, Boolean) ++ (Float *: Tuple), 3]",
      "(Int, Boolean, Float)"
    )

  def testTake_into_rest(): Unit =
    assertTypeIs(
      "type T = Tuple.Take[(Int, Boolean) ++ (Float *: Tuple), 4]",
      "Tuple.Take[Int *: Boolean *: Float *: Tuple, 4]"
    )

  //
  //
  // Tuple.Drop[_, _]
  //
  //

  def testDrop_simple(): Unit =
    assertTypeIs(
      "type T = Tuple.Drop[(Int, String, Boolean), 1]",
      "(String, Boolean)"
    )

  def testDrop_two(): Unit =
    assertTypeIs(
      "type T = Tuple.Drop[(Int, String, Boolean), 2]",
      "Tuple1[Boolean]"
    )

  def testDrop_aliased(): Unit =
    assertTypeIs(
      """type Tup = (Int, String, Double)
        |type T = Tuple.Drop[Tup, 2]
        |""".stripMargin,
      "Tuple1[Double]"
    )

  def testDrop_abstract_tuple(): Unit =
    assertTypeIs(
      "type T[X] = Tuple.Drop[X, 1]",
      "Tuple.Drop[X, 1]"
    )

  def testDrop_abstract_n(): Unit =
    assertTypeIs(
      "type T[N] = Tuple.Drop[(Int, String), N]",
      "Tuple.Drop[(Int, String), N]"
    )

  def testDrop_complex_types(): Unit =
    assertTypeIs(
      "type T = Tuple.Drop[(List[Int], Map[String, Int], Option[Double]), 1]",
      "(Map[String, Int], Option[Double])"
    )

  def testDrop_before_rest(): Unit =
    assertTypeIs(
      "type T = Tuple.Drop[(Int, Boolean) ++ (Float *: Tuple), 3]",
      "Tuple"
    )

  def testDrop_into_rest(): Unit =
    assertTypeIs(
      "type T = Tuple.Drop[(Int, Boolean) ++ (Float *: Tuple), 4]",
      "Tuple.Drop[Int *: Boolean *: Float *: Tuple, 4]"
    )

  //
  //
  // Tuple.Split[_, _]
  //
  //

  def testSplit_simple(): Unit =
    assertTypeIs(
      "type T = Tuple.Split[(Int, String, Boolean), 1]",
      "(Tuple1[Int], (String, Boolean))"
    )

  def testSplit_middle(): Unit =
    assertTypeIs(
      "type T = Tuple.Split[(Int, String, Boolean), 2]",
      "((Int, String), Tuple1[Boolean])"
    )

  def testSplit_aliased(): Unit =
    assertTypeIs(
      """type Tup = (Int, String, Double)
        |type T = Tuple.Split[Tup, 1]
        |""".stripMargin,
      "(Tuple1[Int], (String, Double))"
    )

  def testSplit_abstract_tuple(): Unit =
    assertTypeIs(
      "type T[X] = Tuple.Split[X, 1]",
      "(Tuple.Take[X, 1], Tuple.Drop[X, 1])" // should be Tuple.Split[X, 1]?
    )

  def testSplit_abstract_n(): Unit =
    assertTypeIs(
      "type T[N] = Tuple.Split[(Int, String), N]",
      "(Tuple.Take[(Int, String), N], Tuple.Drop[(Int, String), N])" // should be Tuple.Split[(Int, String), N]?
    )

  def testSplit_complex_types(): Unit =
    assertTypeIs(
      "type T = Tuple.Split[(List[Int], Map[String, Int], Option[Double]), 2]",
      "((List[Int], Map[String, Int]), Tuple1[Option[Double]])"
    )

  def testSplit_before_rest(): Unit =
    assertTypeIs(
      "type T = Tuple.Split[(Int, Boolean) ++ (Float *: Tuple), 3]",
      "((Int, Boolean, Float), Tuple)"
    )

  def testSplit_into_rest(): Unit =
    assertTypeIs(
      "type T = Tuple.Drop[(Int, Boolean) ++ (Float *: Tuple), 4]",
      "Tuple.Drop[Int *: Boolean *: Float *: Tuple, 4]"
    )

  //
  //
  // Tuple.Union[_, _]
  //
  //

  def testUnion_simple(): Unit =
    assertTypeIs(
      "type T = Tuple.Union[(Int, String)]",
      "Int | String"
    )

  def testUnion_same(): Unit =
    assertTypeIs(
      "type T = Tuple.Union[(Int, Int)]",
      "Int"
    )

  def testUnion_with_abstract_inner(): Unit =
    assertTypeIs(
      "type T[X] = Tuple.Union[(X, String)]",
      "X | String"
    )


  //
  //
  // Tuple.Contains[_, _]
  //
  //

  def testContains_simple(): Unit =
    assertTypeIs(
      "type T = Tuple.Contains[(Int, String), Int]",
      "true"
    )

  def testContains_simple_not(): Unit =
    assertTypeIs(
      "type T = Tuple.Contains[(Int, String), Boolean]",
      "false"
    )

  def testContains_subtyping(): Unit =
    assertTypeIs(
      "type T = Tuple.Contains[(1, String), Int]",
      "true"
    )

  def testContains_alias(): Unit =
    assertTypeIs(
      """type Tup = (Int, String)
        |type T = Tuple.Contains[Tup, Int]
        |""".stripMargin,
      "true"
    )

  def testContains_alias_not(): Unit =
    assertTypeIs(
      """type Tup = (Int, String)
        |type T = Tuple.Contains[Tup, Boolean]
        |""".stripMargin,
      "false"
    )

  def testContains_aliased(): Unit =
    assertConforms(
      """type HasInt[X] = Tuple.Contains[X, Int]
        |type T = HasInt[(Int, String)]
        |""".stripMargin,
      "true"
    )

  def testContains_abstract(): Unit =
    assertTypeIs(
      "type T[X] = Tuple.Contains[X, Boolean]",
      "Tuple.Contains[X, Boolean]"
    )

  def testContains_before_rest(): Unit =
    assertTypeIs(
      "type T = Tuple.Contains[(Int, Boolean) ++ Tuple, Boolean]",
      "true"
    )

  def testContains_not_before_rest(): Unit =
    assertTypeIs(
      "type T = Tuple.Contains[(Int, Boolean) ++ Tuple, Float]",
      "Tuple.Contains[Int *: Boolean *: Tuple, Float]"
    )

  //
  //
  // Tuple.Disjoint[_]
  //
  //

  def testDisjoint_yes(): Unit =
    assertTypeIs(
      "type T = Tuple.Disjoint[(Int, String), (Boolean, Double)]",
      "true"
    )

  def testDisjoint_no(): Unit =
    assertTypeIs(
      "type T = Tuple.Disjoint[(Boolean, Int), (Int, String)]",
      "false"
    )

  def testDisjoint_subtyping(): Unit =
    assertTypeIs(
      "type T = Tuple.Disjoint[(Int, String), (1, 2)]",
      "false"
    )
}