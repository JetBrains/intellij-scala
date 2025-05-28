package org.jetbrains.plugins.scala.annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.element.ScTypeBoundsOwnerAnnotator
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam

class ScTypeBoundsOwnerAnnotatorTest_Scala3 extends AnnotatorSimpleTestCase {

  override protected def scalaVersion: ScalaVersion = ScalaVersion.Latest.Scala_3_LTS

  //SCL-21814
  def testMatchTypes1(): Unit = assertNothing(
    messages(
      """sealed trait BoundedHList[+Bound]
        |
        |case object HNil extends BoundedHList[Nothing]
        |case class HCons[Bound, Head <: Bound, Tail <: BoundedHList[Bound]](head: Head, tail: Tail) extends BoundedHList[Bound]
        |
        |object BoundedHList:
        |
        |  type HListFromTuple[Bound, Tup <: Tuple] <: BoundedHList[Bound] = Tup match
        |    case EmptyTuple => HNil.type
        |    case h *: t => HCons[Bound, h, HListFromTuple[Bound, t]]
        |
        |  private def toBoundedHListHelper[Bound, T <: Tuple](tup: T): HListFromTuple[Bound, T] =
        |    (tup match {
        |      case EmptyTuple => HNil
        |      case h *: t => HCons(h, toBoundedHListHelper(t))
        |    }).asInstanceOf[HListFromTuple[Bound, T]]
        |
        |  extension[T <: Tuple](tup: T)
        |    def toBoundedHList[Bound](using AllInBounds[T, Bound]) = toBoundedHListHelper(tup)
        |
        |  type HeadOf[T <: Tuple] = T match
        |    case h *: _ => h
        |
        |  type TailOf[T <: Tuple] <: Tuple = T match
        |    case _ *: t => t
        |
        |  trait AllInBounds[T <: Tuple, Bound]
        |  given emptyTupInBound[Bound]: AllInBounds[EmptyTuple, Bound] with { }
        |  given recTupInBound[Bound, T <: Tuple](using HeadOf[T] <:< Bound, AllInBounds[TailOf[T], Bound]): AllInBounds[T, Bound] with { }
        |
        |end BoundedHList""".stripMargin
    )
  )

  //SCL-21814
  def testMatchTypes2(): Unit = assertNothing(
    messages(
      """sealed trait MyTuple extends Product:
        |  import MyTuple.*
        |  def map[F[_]](f: [t] => t => F[t]): Map[this.type, F] = ???
        |
        |object MyTuple:
        |  type Append[X <: MyTuple, Y] <: NonEmptyTuple = X match
        |    case EmptyTuple => Y *: EmptyTuple
        |    case x *: xs => x *: Append[xs, Y]
        |
        |  type Fold[Tup <: MyTuple, Z, F[_, _]] = Tup match
        |    case EmptyTuple => Z
        |    case h *: t => F[h, Fold[t, Z, F]]
        |
        |  type Map[Tup <: MyTuple, F[_ <: Union[Tup]]] <: MyTuple = Tup match
        |    case EmptyTuple => EmptyTuple
        |    case h *: t => F[h] *: Map[t, F]
        |
        |  type Union[T <: MyTuple] = Fold[T, Nothing, [x, y] =>> x | y]
        |
        |type EmptyTuple = EmptyTuple.type
        |case object EmptyTuple extends MyTuple
        |sealed trait NonEmptyTuple extends MyTuple
        |sealed abstract class *:[+H, +T <: MyTuple] extends NonEmptyTuple
        |""".stripMargin
    )
  )

  def messages(@Language("Scala 3")code: String): List[Message] = {
    val file: ScalaFile = code.parse

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    file
      .depthFirst()
      .filterByType[ScTypeParam]
      .foreach(ScTypeBoundsOwnerAnnotator.annotate(_, typeAware = true))

    mock.annotations
  }
}
