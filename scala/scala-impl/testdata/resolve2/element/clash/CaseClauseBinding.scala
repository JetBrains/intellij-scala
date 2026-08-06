"foo" match {
  case (a: String, a: String) => {
    println( /* resolved: false */ a.getClass)
    println(classOf[ /* resolved: false */ a])
  }
}
