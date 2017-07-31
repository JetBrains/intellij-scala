trait Computable[T] {
  def compute: T
}

object Processor {
  def process(run: Runnable) = ???
  def process[T](comp: Computable[T]): T = ???
}


class Test {

  val seq: Seq[Any] = ???
  val computable: Computable[Iterable[String]] = ???
  val bug = seq.flatMap(f => {
    Processor.<ref>process(computable)
  })
}
