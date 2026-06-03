trait Computable[T] {
  def compute: T
}

object Processor {
  def process(comp: Runnable) = ???
  def process[T](comp: Computable[T]): T = ???
}


class Test {

  val seq: Seq[Any] = ???
  val computable: Computable[Iterable[String]] = ???
  val bug = seq.flatMap(f => {
    val res = Processor.<ref>process(computable)
    res
  })
}
