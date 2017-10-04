class SimpleResolve {
  private def foo = 44
  val x = /*ref*/foo
}
//true