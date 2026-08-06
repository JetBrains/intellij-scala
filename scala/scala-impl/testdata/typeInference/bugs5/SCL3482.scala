object P {
object Foo {
  implicit def string2int(x: String) = x.length
  implicit def tuple2int(x: (String, String)) : (Int, Int) = (x._1, x._2)
}

object Main {
  import Foo.tuple2int
  implicit val x: Int = 123
  case class A(x: B[Int, Int])
  case class B[T, Y](x: (T, Y))(implicit xz: Int)
  A(/*start*/B("" -> "")/*end*/)
}
}
//Main.B[Int, Int]