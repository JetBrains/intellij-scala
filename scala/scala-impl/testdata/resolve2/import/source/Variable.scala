object Wrapper {
  var v = {
    case class C
  }

  import /* resolved: false */ v.C

  println(/* resolved: false */ C.getClass)
  println(classOf[ /* resolved: false */ C])
}