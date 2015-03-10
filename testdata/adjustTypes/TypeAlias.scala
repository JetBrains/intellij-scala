import scala.collection.mutable.ArrayBuffer

class TypeAlias {
  type AB[T] = ArrayBuffer[T]
  type JInt = java.lang.Integer

  val x: /*start*/ArrayBuffer[java.lang.Integer]/*end*/ = ArrayBuffer(1)
}

/*
import scala.collection.mutable.ArrayBuffer

class TypeAlias {
  type AB[T] = ArrayBuffer[T]
  type JInt = java.lang.Integer

  val x: /*start*/AB[JInt]/*end*/ = ArrayBuffer(1)
}
*/