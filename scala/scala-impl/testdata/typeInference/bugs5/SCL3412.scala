object SCL3412 {
  abstract class SomeClass[A] {
    def get(): A
  }

  def m[A]()(implicit manifestA: scala.reflect.Manifest[A]): SomeClass[A] =
    manifestA.toString match {
      case "Int" => new SomeClass[A] {
        def get() = 5.asInstanceOf[A]
      }
    }

  def main(args: Array[String]) {
    val t = m[Int]
    /*start*/t.get()/*end*/
  }
}
//Int