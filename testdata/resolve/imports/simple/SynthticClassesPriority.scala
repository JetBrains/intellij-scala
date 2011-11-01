object U {
  case object Unit
}


object B {
  import U.Unit

  val x = <ref>Unit
}