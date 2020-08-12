package org.jetbrains.plugins.scala.dfa.testutils

import org.scalactic.Prettifier
import org.scalactic.source.Position
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.prop.Tables.Table

trait ForAllGenerator[T] {
  def generate: Seq[T]
}

object ForAllGenerator {
  def apply[T](first: T, rest: T*): ForAllGenerator[T] = from(first +: rest)
  def from[T](all: Seq[T]): ForAllGenerator[T] = new ForAllGenerator[T] {
    override def generate: Seq[T] = all
  }
}

trait ForAllChecker {
  import ForAllChecker._

  def forAll[T1: ForAllGenerator, ASS](f: T1 => ASS)(implicit asserting : org.scalatest.enablers.TableAsserting[ASS], prettifier : Prettifier, pos : Position) : asserting.Result =
    TableDrivenPropertyChecks.forAll(
      Table("x", gen[T1]: _*)
    )(f)(asserting, prettifier, pos)

  def forAll[T1: ForAllGenerator, T2: ForAllGenerator, ASS](f: (T1, T2) => ASS)(implicit asserting : org.scalatest.enablers.TableAsserting[ASS], prettifier : Prettifier, pos : Position) : asserting.Result =
    TableDrivenPropertyChecks.forAll(
      Table(
        ("x", "y"),
        gen[(T1, T2)]: _*
      )
    )(f)(asserting, prettifier, pos)

  def forAll[T1: ForAllGenerator, T2: ForAllGenerator, T3: ForAllGenerator, ASS](f: (T1, T2, T3) => ASS)(implicit asserting : org.scalatest.enablers.TableAsserting[ASS], prettifier : Prettifier, pos : Position) : asserting.Result =
    TableDrivenPropertyChecks.forAll(
      Table(
        ("x", "y", "z"),
        gen[(T1, T2, T3)]: _*
      )
    )(f)(asserting, prettifier, pos)
}

object ForAllChecker extends ForAllChecker {
  private def gen[T: ForAllGenerator]: Seq[T] = implicitly[ForAllGenerator[T]].generate

  private implicit def tuple2Gen[T1: ForAllGenerator, T2: ForAllGenerator]: ForAllGenerator[(T1, T2)] = new ForAllGenerator[(T1, T2)] {
    override def generate: Seq[(T1, T2)] = for (x <- gen[T1]; y <- gen[T2]) yield (x, y)
  }

  private implicit def tuple3Gen[T1: ForAllGenerator, T2: ForAllGenerator, T3: ForAllGenerator]: ForAllGenerator[(T1, T2, T3)] = new ForAllGenerator[(T1, T2, T3)] {
    override def generate: Seq[(T1, T2, T3)] = for (x <- gen[T1]; y <- gen[T2]; z <- gen[T3]) yield (x, y, z)
  }
}