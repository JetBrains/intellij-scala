for (a: String <- List[String]();
     val b: String = a.toString;
     val b: String = a.toString) {
  println( /* resolved: false */ b.getClass)
  println(classOf[ /* resolved: false */ b])
}
