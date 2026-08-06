package types

trait FunctionContext {
  type T1 = Int ?=> Unit

  type T2 = (Int, Long) ?=> Unit

  def repeated(xs: (Int ?=> Unit)*): Unit
}