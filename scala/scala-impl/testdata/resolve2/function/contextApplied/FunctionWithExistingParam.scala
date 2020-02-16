trait Log[F[_]] {
  def log(msg: String): F[Unit]
}

def foo[T[_]: Log](T: Unit) = /* resolved: true */T./* resolved: false */log("Hello")
