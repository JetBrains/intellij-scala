object SCL5030 {
import java.lang.{Long => JLong}

object stuff {
  import Test._

  trait TaggedField[T, U] {
    def asTagged: U
  }

  class MyIdField(val value: Long) extends TaggedField[Long, MyId] {
    override def asTagged: MyId = Tag[JLong, _MyId](value)
  }

  implicit def idToField(id: MyId): MyIdField = new MyIdField(id)
  implicit def fieldToId(f: MyIdField): MyId = f.asTagged

}

import stuff._

object Tag {
  type Tagged[U] = { type Tag = U }
  type @@[T, U] = T with Tagged[U] with Object
  @inline def apply[A, T](a: A): A @@ T = a.asInstanceOf[A @@ T]
}

object Test {
  import Tag._

  sealed trait _MyId
  type MyId = JLong @@ _MyId
}

class Foo {
  import Test._
  def foo(x: MyId): Int = 1
  def foo(x: String): String = x

  def goo(x: MyIdField): Int = 1
  def goo(x: String): String = x

  val x: MyId = new MyIdField(4)
  /*start*/(foo(new MyIdField(4)), goo(x))/*end*/
}
}
//(Int, Int)