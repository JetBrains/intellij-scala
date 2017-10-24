case class Foo[F[_], A](fab: F[Option[A]])
case class Bar[F[_], A](value: F[A])
type T[A] = Bar[List, A]
val x: Foo[T, String] = Foo(Bar(List.empty[Option[String]]))
//true