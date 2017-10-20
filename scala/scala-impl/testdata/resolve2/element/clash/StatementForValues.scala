for (a: String <- List[String]();
     a: String <- List[String]()) {
  println( /* line: 2 */ a.getClass)
  println(classOf[ /* resolved: false */ a])
}
