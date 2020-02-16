trait Log[F[_]] {
  def log(msg: String): F[Unit]
}

class Foo(val dummy: Boolean) extends AnyVal {
  def bar[T[_]: Log] = /* resolved: false */ T.log("Hello")
}
