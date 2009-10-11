class Param[T] {
  def act(f: T => String) {}
}

val foo = new Param[String]
foo.act(x => /*start*/x/*end*/.toString)
//String