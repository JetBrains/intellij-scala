package org.jetbrains.plugins.scala.lang.typeInference

import com.intellij.openapi.util.registry.Registry
import junit.framework.TestCase.assertEquals
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScMatchType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.typeInference.shims.TupleIntrinsicsTest
import org.jetbrains.plugins.scala.project.ScalaFeatures

class MatchTypeReductionTest extends TupleIntrinsicsTest {
  override protected def setUp(): Unit = {
    super.setUp()
    Registry.get("scala.enable.match.type.intrinsics").setValue(false)
  }

  override def assertDoesNotReduce(code: String, tpe: String): Unit =
    assertTypeIs(code, tpe, dealias = false)

  override def assertTypeIs(code: String, tpe: String): Unit =
    assertTypeIs(code, tpe, dealias = true)

  private def assertTypeIs(code: String, tpe: String, dealias: Boolean): Unit = {
    val file =
      ScalaPsiElementFactory.createScalaFileFromText(
        transformCode(code),
        ScalaFeatures.onlyByVersion(version)
      )(getProject)

    val typeElement = file.getLastChild.getLastChild.asInstanceOf[ScTypeElement]

    val actual =
      typeElement
        .`type`()
        .toOption
        .map { tpe =>
          val res =
            if (dealias) tpe.removeAliasDefinitions().updateRecursively {
              case mt: ScMatchType => mt.reduce.getOrElse(mt)
            }
            else tpe

          val text = res.presentableText(TypePresentationContext(typeElement), Context.Empty)
          text
        }
        .getOrElse("<error>")


    assertEquals(tpe, actual)
  }


  override def testUnion_same(): Unit =
    assertTypeIs(
      "type T = Tuple.Union[(Int, Int)]",
      "Int | (Int | Nothing)"
    )

  override def testUnion_simple(): Unit =
    assertTypeIs(
      "type T = Tuple.Union[(Int, String)]",
      "Int | (String | Nothing)"
    )

  override def testUnion_with_abstract_inner(): Unit =
    assertTypeIs(
      "type T[X] = Tuple.Union[(X, String)]",
      "X | (String | Nothing)"
    )

  override def testSplit_into_rest(): Unit =
    assertDoesNotReduce(
      "type T = Tuple.Drop[(Int, Boolean) ++ (Float *: Tuple), 4]",
      "Tuple.Drop[(Int, Boolean) ++ Float *: Tuple, 4]"
    )

  override def testDrop_into_rest(): Unit =
    assertDoesNotReduce(
      "type T = Tuple.Drop[(Int, Boolean) ++ (Float *: Tuple), 4]",
      "Tuple.Drop[(Int, Boolean) ++ Float *: Tuple, 4]"
    )

  override def testTake_into_rest(): Unit =
    assertDoesNotReduce(
      "type T = Tuple.Take[(Int, Boolean) ++ (Float *: Tuple), 4]",
      "Tuple.Take[(Int, Boolean) ++ Float *: Tuple, 4]"
    )

  override def testElem_elem_is_in_rest(): Unit =
    assertDoesNotReduce(
      "type T = Tuple.Elem[(Int, Boolean) ++ NonEmptyTuple, 2]",
      "Tuple.Elem[(Int, Boolean) ++ NonEmptyTuple, 2]"
    )

  override def testContains_not_before_rest(): Unit =
    assertDoesNotReduce(
      "type T = Tuple.Contains[(Int, Boolean) ++ Tuple, Float]",
      "Tuple.Contains[(Int, Boolean) ++ Tuple, Float]"
    )
}
