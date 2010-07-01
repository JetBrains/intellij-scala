def bar(foo: String*) = {
  foo.map(_.contains("a").toString)
}
//Seq[String]