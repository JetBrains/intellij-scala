case class Foo[F[_], A](fab: F[Option[A]])
case class Bar[F[_], A](value: F[A])
var y = Foo(Bar(List.empty[Option[String]]))
/*start*/y.fab/*end*/
//Bar[List, Option[String]]
