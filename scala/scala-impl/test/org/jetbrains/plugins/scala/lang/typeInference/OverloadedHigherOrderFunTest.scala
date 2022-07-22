package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class OverloadedHigherOrderFunTest extends ScalaLightCodeInsightFixtureTestAdapter {
  override protected def supportedIn(version: ScalaVersion) = version  >= LatestScalaVersions.Scala_2_13

  def testOverloadedProto(): Unit = checkTextHasNoErrors(
    """
      |object Util {
      |  def mono(x: Int) = x
      |  def poly[T](x: T): T = x
      |}
      |
      |trait FunSam[-T, +R] { def apply(x: T): R }
      |
      |
      |trait TFun { def map[T](f: T => Int): Unit = () }
      |object Fun extends TFun { import Util._
      |  def map[T: scala.reflect.ClassTag](f: T => Int): Unit = ()
      |
      |  map(mono)
      |  map(mono _)
      |  map(x => mono(x))
      |
      |  // can't infer polymorphic type for function parameter:
      |  //  map(poly)
      |  //  map(poly _)
      |  //  map(x => poly(x))
      |}
      |
      |trait TSam { def map[T](f: T FunSam Int): Unit = () }
      |object Sam extends TSam { import Util._
      |  def map[T: scala.reflect.ClassTag](f: T `FunSam` Int): Unit = ()
      |
      |  map(mono) // sam
      |  map(mono _) // sam
      |  map(x => mono(x)) // sam
      |
      |  // can't infer polymorphic type for function parameter:
      |  //  map(poly)
      |  //  map(poly _)
      |  //  map(x => poly(x))
      |}
      |
      |trait IntFun { def map[T](f: Int => T): Unit = () }
      |object int_Fun extends IntFun { import Util._
      |  def map[T: scala.reflect.ClassTag](f: Int => T): Unit = ()
      |
      |  map(mono)
      |  map(mono _)
      |  map(x => mono(x))
      |
      |  map(poly)
      |  map(poly _)
      |  map(x => poly(x))
      |}
      |
      |trait IntSam { def map[T](f: Int FunSam T): Unit = () }
      |object int_Sam extends IntSam { import Util._
      |  def map[T: scala.reflect.ClassTag](f: Int `FunSam` T): Unit = ()
      |
      |  map(mono) // sam
      |  map(mono _) // sam
      |  map(x => mono(x)) // sam
      |
      |  map(poly) // sam
      |  map(poly _) // sam
      |  map(x => poly(x)) // sam
      |}
      |""".stripMargin)

  def testOverloadedProtoCollapse(): Unit = checkTextHasNoErrors(
    """
      |class Test {
      |  def prepended[B >: Char](elem: B): String = ???
      |  def prepended(c: Char): String = ???
      |
      |  def +:[B >: Char](elem: B): String = prepended(elem)
      |}
      |
      |
      |trait DurationConversions {
      |  trait Classifier[C] { type R }
      |
      |  def days: Int = ???
      |  def days[C](c: C)(implicit ev: Classifier[C]): ev.R = ???
      |
      |  def day[C](c: C)(implicit ev: Classifier[C]): ev.R = ???
      |}
      |
      |
      |trait AnonMatch {
      |  trait MapOps[K, +V, +CC[_, _]] {
      |    def map[K2, V2](f: ((K, V)) => (K2, V2)): CC[K2, V2] = ???
      |    def map[K2 <: AnyRef, V2](f: ((K with AnyRef, V)) => (K2, V2)): MapOps[K2, V2, Map] = ???
      |  }
      |
      |  (??? : MapOps[String, Int, Map]).map{ case (k,v) => ??? }
      |}
      |
      |
      |trait FBounds {
      |  def f[A](x: A) = 11;
      |  def f[A <: Ordered[A]](x: Ordered[A]) = 12;
      |
      |  f(1)
      |}
      |
      |// Don't collapse A and Tree[A]. Naively replacing type params with ? gives ? and Tree[?],
      |// which are equal because wildcard equals whatever
      |// example from specs2
      |class Trees { outer =>
      |  trait Tree[B]
      |
      |  def clean[A](t: Tree[Option[A]]): Tree[A] =
      |    prune(t, (a: Option[A]) => a).getOrElse(??? : Tree[A])
      |
      |  def prune[A, B](t: Tree[A], f: A => Option[B]): Option[Tree[B]] = ???
      |  def prune[A](t: Tree[A], f: Tree[A] => Option[A])(implicit initial: A): Tree[A] = ???
      |}
      |
      |
      |// From gigahorse
      |abstract class Sam[A] { def apply(a: String): A }
      |
      |class GigaHorse {
      |  def map[A](f: String => A): A = map(new Sam[A] { def apply(a: String): A = f(a) })
      |  def map[A](f: Sam[A]): A = ???
      |}
      |""".stripMargin)

  def testOverloadedHoFun(): Unit = checkTextHasNoErrors(
    """
      |import scala.math.Ordering
      |import scala.reflect.ClassTag
      |
      |trait Sam { def apply(x: Int): String }
      |trait SamP[U] { def apply(x: Int): U }
      |
      |class OverloadedFun[T](x: T) {
      |  def foo(f: T => String): String = f(x)
      |  def foo(f: Any => T): T = f("a")
      |
      |  def poly[U](f: Int => String): String = f(1)
      |  def poly[U](f: Int => U): U = f(1)
      |
      |  def polySam[U](f: Sam): String = f(1)
      |  def polySam[U](f: SamP[U]): U = f(1)
      |
      |  // check that we properly instantiate java.util.function.Function's type param to String
      |  def polyJavaSam(f: String => String) = 1
      |  def polyJavaSam(f: java.util.function.Function[String, String]) = 2
      |}
      |
      |class StringLike(xs: String) {
      |  def map[A](f: Char => A): Array[A] = ???
      |  def map(f: Char => Char): String = ???
      |}
      |
      |object Test {
      |  val of = new OverloadedFun[Int](1)
      |
      |  //  of.foo(_.toString) // not allowed -- different argument types for the hof arg
      |
      |  of.poly(x => x / 2 )
      |  //  of.polySam(x => x / 2) // not allowed -- need at least one regular function type in the mix
      |  of.polyJavaSam(x => x)
      |
      |  val sl = new StringLike("a")
      |  sl.map(_ == 'a')  // : Array[Boolean]
      |  sl.map(x => 'a')  // : String
      |}
      |
      |object sorting {
      |  def stableSort[K: ClassTag](a: Seq[K], f: (K, K) => Boolean): Array[K] = ???
      |  def stableSort[L: ClassTag](a: Array[L], f: (L, L) => Boolean): Unit = ???
      |
      |  stableSort(??? : Seq[Boolean], (x: Boolean, y: Boolean) => x && !y)
      |}
      |
      |// trait Bijection[A, B] extends (A => B) {
      |//   def andThen[C](g: Bijection[B, C]): Bijection[A, C] = ???
      |//   def compose[T](g: Bijection[T, A]) = g andThen this
      |// }
      |
      |object SI10194 {
      |  trait X[A] {
      |    def map[B](f: A => B): Unit
      |  }
      |
      |  trait Y[A] extends X[A] {
      |    def map[B](f: A => B)(implicit ordering: Ordering[B]): Unit
      |  }
      |
      |  trait Z[A] extends Y[A]
      |
      |  (null: Y[Int]).map(x => x.toString) // compiled
      |  (null: Z[Int]).map(x => x.toString) // didn't compile
      |}
      |""".stripMargin)

  def testEA232097(): Unit = checkTextHasNoErrors(
    """
      |import java.io.File
      |import java.io.FilenameFilter
      |case class ExactMatchFilter(fileName: String) extends FilenameFilter {
      |  override def accept(dir: File, name: String): Boolean = name == fileName
      |}
      |
      |def postProcessDownload(extractedDownloadable: File): Unit = {
      |  val files = extractedDownloadable.listFiles(ExactMatchFilter(if (true) "play.bat" else "play"))
      |  ???
      |}""".stripMargin
  )
}
