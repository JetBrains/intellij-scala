for (a: String <- List[String]();
     a: String <- List[String]()) {
  println( /* resolved: false */ a.getClass)
  println(classOf[ /* resolved: false */ a])
}
