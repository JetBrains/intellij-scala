trait Log[F[_]] {
  def log(msg: String): F[Unit]
}

trait Console[F[_]] {
  def read: F[Unit]
}

def foo[T[_]: Log : Console] = {
  /* resolved: true */T./* offset: 24 */log("Hello")
  /* resolved: true */T./* offset: 81 */read
}
