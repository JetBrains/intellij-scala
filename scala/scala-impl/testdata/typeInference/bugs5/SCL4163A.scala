abstract class Expr[T] {

  def args: Iterable[Expr[_]]

  def fold[A](v: A)(f: (A, Expr[_]) => A): A =
    f(args.foldLeft(v) { (a, b) => /*start*/b.fold(a)(f)/*end*/ }, this)   // f underlined (Expected (Nothing, Expr[_]) => Nothing, actual (A, Expr[_]) => A)
}
//A