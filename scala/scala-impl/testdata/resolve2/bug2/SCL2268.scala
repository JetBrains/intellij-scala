import java.util.Date

class Opt[T, R](f : (R, T) => Unit) {
  def apply(opt : T) : R => Unit = f(_, opt)
}

val v = new Opt[Int, Date](_ /* */setHours _) 