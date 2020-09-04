package scala.tasty

package object reflect {
  type TypeTest[T, U] = scala.reflect.ClassTag[U]
}
