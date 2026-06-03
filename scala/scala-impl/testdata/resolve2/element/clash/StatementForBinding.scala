for ((a: String, a: String) <- Map[String, String]()) {
  println(/* resolved: false */ a.getClass)
  println(classOf[/* resolved: false */ a])
}
