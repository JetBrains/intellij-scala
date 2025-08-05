package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, TypecheckerTests}
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class Scala3OpaqueTypeAliasIntegrationTest extends ScalaLightCodeInsightFixtureTestCase {
  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_3_0

  def testScl20761(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Scope {
         |  class MyClass
         |  opaque type MyOpaqueType = String
         |  type MyTypeClass = String
         |
         |  extension (t: MyTypeClass)
         |    def myExtensionForAlias: String = "42"
         |
         |  extension (t: MyClass)
         |    def myExtensionForClass: String = "42"
         |
         |  extension (t: MyOpaqueType)
         |    def myExtensionForOpaque: String = "42"
         |}
         |
         |val valueClass: Scope.MyClass = new Scope.MyClass
         |val valueOpaque: Scope.MyOpaqueType = ("qwe").asInstanceOf[Scope.MyOpaqueType]
         |val valueTypeAlias: Scope.MyTypeClass = ("qwe").asInstanceOf[Scope.MyTypeClass]
         |
         |valueClass.myExtensionForClass
         |valueOpaque.myExtensionForOpaque
         |""".stripMargin
    )
  }

  def testScl21060(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Scope:
         |  class MyClass
         |  object MyClass:
         |    extension (t: MyClass)
         |      def myExtensionForClass: String = "42"
         |
         |  opaque type MyOpaqueType = String
         |  object MyOpaqueType:
         |    extension (t: MyOpaqueType)
         |      def myExtensionForOpaque: String = "42"
         |
         |def main(): Unit =
         |  val valueClass: Scope.MyClass = ???
         |  val valueOpaque: Scope.MyOpaqueType = ???
         |
         |  valueClass.myExtensionForClass
         |  valueOpaque.myExtensionForOpaque
         |""".stripMargin
    )
  }

  def testScl21568(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Opaque:
         |  object Scope:
         |    opaque type MyOpaqueType = Nothing
         |
         |    object MyOpaqueType:
         |      extension (t: MyOpaqueType)
         |        def myExtensionForOpaque: String = "42"
         |
         |  def main(): Unit =
         |    val valueOpaque: Scope.MyOpaqueType = ???
         |    valueOpaque.myExtensionForOpaque
         |""".stripMargin
    )
  }

  def testScl22062(): Unit = {
    checkTextHasNoErrors(
      s"""
         |class A {
         |  def fn(x: IArray[Float]): Unit = {}
         |  def fn(x: IArray[Double]): Unit = {}
         |}
         |""".stripMargin
    )
  }

  def testScl22480(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object MainX {
         |  trait Base
         |  case class Child(x: String) extends Base
         |
         |  opaque type Id[+T <: Base] = Int
         |
         |  def update[T <: Base](id: Id[T])(update: (Double, T) => T) = ()
         |
         |  def main(args: Array[String]): Unit = {
         |    val id: Id[Child] = ???
         |    update(id)((x, child) => child.copy(x = ""))
         |  }
         |}
         |""".stripMargin
    )
  }

  def testScl22871(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Native {
         |  case class Vec3(x: Double, y: Double, z: Double)
         |}
         |
         |object Types {
         |  opaque type Vector3f >: Null = Native.Vec3
         |
         |  object Vector3f {
         |    def apply(x: Double, y: Double, z: Double): Vector3f = Native.Vec3(x, y, z)
         |  }
         |
         |  extension (a: Vector3f) {
         |    def + (b: Vector3f): Vector3f = Vector3f(a.x + b.x, a.y + b.y, a.z + b.z)
         |    def * (b: Vector3f): Vector3f = Vector3f(a.x * b.x, a.y * b.y, a.z * b.z)
         |  }
         |}
         |
         |object Main {
         |  import Types.Vector3f
         |
         |  def main(args: Array[String]): Unit = {
         |    val v = Vector3f(0, 1, 2)
         |    val m = v * v
         |    val p = v + v
         |    println(m)
         |    println(p)
         |  }
         |}
         |""".stripMargin
    )
  }

  def testScl23225(): Unit = {
    checkHasErrorAroundCaret(
      s"""
         |object Native {
         |  class V(val x: Double, val y: Double) {
         |    def c(v: V) = V(v.x, v.y)
         |  }
         |}
         |object Types {
         |  import Native.*
         |  opaque type Vec = V
         |  object Vec {
         |    def apply(x: Double = 0, y: Double = 0): Vec = V(x, y)
         |  }
         |}
         |
         |import Types.*
         |
         |object Main {
         |  def main(args: Array[String]): Unit = {
         |    val vec = Vec(0, 1)
         |    vec.${CARET}c(v = vec)
         |  }
         |}
         |""".stripMargin
    )
  }

  def testScl23226(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Native {
         |  type CoordT = Double
         |  class V(val x: CoordT, val y: CoordT) {
         |    def copy(v: V) = V(v.x, v.y)
         |  }
         |}
         |object Types {
         |  import Native.*
         |  opaque type Vec = V
         |  object Vec {
         |    def apply(x: CoordT = 0, y: CoordT = 0): Vec = V(x, y)
         |  }
         |  extension (v: Vec) {
         |    def copy(x: CoordT = v.x, y: CoordT = v.y): Vec = V(x, y)
         |  }
         |}
         |
         |import Types.*
         |
         |object Main {
         |  def main(args: Array[String]): Unit = {
         |    val vec = Vec(0, 1)
         |    vec.copy(y = 1)
         |  }
         |}
         |""".stripMargin
    )
  }

  def testScl23227(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Native {
         |  class V(val x: Double, val y: Double) {
         |    def cross(v: V): V = ???
         |  }
         |}
         |object Types {
         |  import Native.*
         |  opaque type Vec = V
         |  object Vec {
         |    def apply(x: Double = 0, y: Double = 0): Vec = V(x, y)
         |  }
         |  extension (v: Vec) {
         |    def cross(w: Vec): Vec = ???
         |    def normalized: Vec = ???
         |  }
         |}
         |
         |import Types.*
         |
         |object Main {
         |  def main(args: Array[String]): Unit = {
         |    val a = Vec(0, 1)
         |    val b = Vec(2, 3)
         |    val x = a.cross(b)
         |    x.normalized
         |  }
         |}
         |""".stripMargin
    )
  }

  // We fail to resolve *
  // See SCL-23940 for the problem
  //
  //def testScl23232(): Unit = {
  //  checkTextHasNoErrors(
  //    s"""
  //       |object Native {
  //       |  case class V(x: Double, y: Double)
  //       |
  //       |  trait Vectoric[V] {
  //       |    def timesScalar(v: V, x: Double): V
  //       |  }
  //       |
  //       |  extension (f: Double) {
  //       |    def *[V](v: V)(using vec: Vectoric[V]): V = vec.timesScalar(v, f)
  //       |  }
  //       |}
  //       |
  //       |import Native.*
  //       |
  //       |object Types {
  //       |  opaque type Vec = V
  //       |  object Vec {
  //       |    def apply(x: Double = 0, y: Double = 0): Vec = V(x, y)
  //       |  }
  //       |
  //       |
  //       |  given vecIsVectoric: Vectoric[Vec] with {
  //       |    def timesScalar(v: Vec, f: Double): Vec = new Vec(v.x * f, v.y * f)
  //       |  }
  //       |}
  //       |
  //       |import Types.*
  //       |
  //       |object Main {
  //       |  def shrink(vec: Vec): Vec = {
  //       |    0.5 * vec
  //       |  }
  //       |  def main(args: Array[String]): Unit = {
  //       |    val a = Vec(0, 1)
  //       |    println(shrink(a))
  //       |  }
  //       |}
  //       |""".stripMargin
  //  )
  //}

  def testScl23233(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Native {
         |  case class V(x: Double, y: Double)
         |  trait Vectoric[V]
         |}
         |
         |import Native.*
         |
         |object Types {
         |  opaque type Vec = V
         |  object Vec {
         |    def apply(x: Double = 0, y: Double = 0): Vec = V(x, y)
         |  }
         |
         |  def pass[V: Vectoric](a: V): V = a
         |
         |  given vecIsVectoric: Vectoric[Vec] with {}
         |}
         |
         |import Types.*
         |
         |object Main {
         |  def main(args: Array[String]): Unit = {
         |    val a = Vec(0, 1)
         |    println(pass(a))
         |  }
         |}
         |""".stripMargin
    )
  }

  def testScl23236(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Types {
         |  class O {
         |    def matrix: String = "matrix"
         |  }
         |
         |  opaque type Obj >: Null <: AnyRef = O
         |
         |  object Obj {
         |    def apply(): Obj = new O
         |  }
         |  extension (o: Obj) {
         |    def m = o.matrix
         |  }
         |}
         |
         |import Types.*
         |
         |object Main {
         |  def main(args: Array[String]): Unit = {
         |    val o = Obj()
         |    o.m
         |  }
         |}
         |""".stripMargin
    )
  }

  def testScl23626(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Native {
         |  case class Vec3(x: Double, y: Double, z: Double)
         |}
         |
         |object Types {
         |  opaque type Vector3f = Native.Vec3
         |
         |  trait Vectoric[T]
         |
         |  trait VectoricOps {
         |    extension [V](lhs: V)(using v: Vectoric[V]) {
         |      def ext: String = lhs.toString
         |    }
         |  }
         |
         |  given Vectoric[Vector3f] = ???
         |
         |  object Vector3f extends VectoricOps {
         |    def apply(x: Double, y: Double, z: Double): Vector3f = Native.Vec3(x, y, z)
         |  }
         |}
         |
         |object Main {
         |  import Types.*
         |
         |  def main(args: Array[String]): Unit = {
         |    val v = Vector3f(0, 1, 2)
         |    println(v.ext)
         |  }
         |}
         |""".stripMargin
    )
  }

  def testScl23630(): Unit = {
    checkTextHasNoErrors(
      s"""
         |trait Bar[A]
         |object A {
         |  opaque type Foo = Any
         |  given Bar[Foo] = ???
         |}
         |
         |object Test {
         |  import A.Foo
         |  implicitly[Bar[Foo]]
         |}
         |""".stripMargin
    )
  }

  def testScl23656(): Unit = {
    checkTextHasNoErrors(
      s"""
         |trait MyService {
         |  def find(x: Int): String = ""
         |}
         |
         |opaque type Stub[+A] <: A = A
         |
         |object Stub {
         |  def apply[A](a: A): Stub[A] = a
         |}
         |
         |@main def hello =
         |  val myList = Stub(new MyService {})
         |  myList.find(42)
         |""".stripMargin
    )
  }

  def testScl23705(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Native {
         |  case class Vec3(x: Double, y: Double, z: Double)
         |}
         |
         |object Types {
         |  opaque type Vector3f = Native.Vec3
         |
         |  object Vector3f {
         |    def apply(x: Double, y: Double, z: Double): Vector3f = Native.Vec3(x, y, z)
         |    extension (v: Vector3f) {
         |      def ext: String = v.toString
         |    }
         |  }
         |}
         |
         |object Main {
         |  import Types.*
         |
         |  def main(args: Array[String]): Unit = {
         |
         |    Some(Vector3f(0, 1, 2)) match {
         |      case Some(vv) =>
         |        vv.ext
         |    }
         |  }
         |}
         |""".stripMargin
    )
  }

  def testScl23710(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Native {
         |  case class Vec3(x: Double, y: Double, z: Double) {
         |    def add(v: Vec3) = Vec3(x + v.x, y + v.y, z + v.z)
         |  }
         |}
         |
         |object Types {
         |  opaque type Vector3f = Native.Vec3
         |  object Vector3f extends VectoricOps {
         |    def apply(x: Double, y: Double, z: Double): Vector3f = Native.Vec3(x, y, z)
         |  }
         |
         |  trait Vectoric[V] {
         |    def plus(a: V, b: V): V
         |  }
         |
         |  implicit object Vector3fIsVectoric extends Vectoric[Vector3f] {
         |    override def plus(a: Vector3f, b: Vector3f): Vector3f = a add b
         |  }
         |
         |  trait VectoricOps {
         |    extension [V](lhs: V)(using v: Vectoric[V]) {
         |      def +(rhs: V): V = v.plus(lhs, rhs)
         |    }
         |  }
         |}
         |
         |object Main {
         |  import Types.Vector3f
         |
         |  def main(args: Array[String]): Unit = {
         |    val v = Vector3f(0, 1, 2)
         |    val p = v + v
         |    println(p)
         |  }
         |}
         |""".stripMargin
    )
  }

  def testScl23816(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object Wrapper {
         |  opaque type ReqId <: Option[String] = Option[String]
         |
         |  object ReqId {
         |    def apply(x: Option[String]): ReqId = x
         |  }
         |}
         |
         |object Test {
         |  val x: Option[String] = Wrapper.ReqId(None)
         |}
         |""".stripMargin
    )
  }
}
