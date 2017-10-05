object SCL5871 {
  import scala.reflect.runtime.{universe=>ru}

  def foo(x: ru.TypeTag[String]): Boolean = false
  def foo(x: Int): Int = 2

  def goo(x: scala.reflect.runtime.universe.TypeTag[String]): Boolean = true
  def goo(x: Int) = x

  /*start*/(foo(scala.reflect.runtime.universe.typeTag[String]), goo(ru.typeTag[String]))/*end*/
}
//(Boolean, Boolean)