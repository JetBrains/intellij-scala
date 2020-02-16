trait Base[F[_]] {
  def go(): Unit
}

trait First[F[_]] extends Base[F] {
  def go(): Unit = println("First")
}

trait Second[F[_]] extends Base[F] {
  def go(): Unit = println("Second")
}

def foo[T[_]: First : Second] = /* resolved: true */ T./* offset: 81 */go()

def bar[T[_]: Second : First] = /* resolved: true */ T./* offset: 157 */go()
