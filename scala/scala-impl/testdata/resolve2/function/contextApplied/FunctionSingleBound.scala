trait Log[F[_]] {
  def log(msg: String): F[Unit]
}

def foo[T[_]: Log] = /* resolved: true */T./* offset: 24 */log("Hello")
