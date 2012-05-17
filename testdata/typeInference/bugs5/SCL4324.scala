object SCL4324 {
trait Category[~>[_, _]] {
  def identity[A]: A ~> A
}

object Category {
  implicit def fCat: Category[Function1] = new Category[Function1] {
    def identity[A]: A => A = { x => x }
  }
}

object Main extends App {
  import Category._

  /*start*/fCat.identity(3)/*end*/ // Type mismatch: expected Nothing actual Int
}
}
//Int