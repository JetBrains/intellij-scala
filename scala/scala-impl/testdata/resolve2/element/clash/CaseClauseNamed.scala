"foo" match {
  case (name@Some(v1: String), name@Some(v2: String)) => {
    println(/* resolved: false */ name.getClass)
    println(classOf[ /* resolved: false */ name])
  }
}
