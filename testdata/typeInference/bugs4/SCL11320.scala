case class Foo[F[_], A](fab: F[Option[A]])
case class Bar[F[_], A](value: F[A])
/*start*/Foo(Bar(List.empty[Option[String]]))/*end*/
//Foo[[p0$$] Bar[List, p0$$], String]