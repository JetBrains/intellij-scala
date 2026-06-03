def bar(foo: String*) = {
  /*start*/foo.map(_.contains("a").toString)/*end*/
}
//Seq[String]