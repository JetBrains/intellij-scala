package scala.tasty

package object reflect {
  type IsInstanceOf[T] = scala.reflect.ClassTag[T]
}
