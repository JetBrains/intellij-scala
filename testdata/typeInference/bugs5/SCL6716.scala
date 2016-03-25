object SCL6716{
  val l: List[String] = List("another string").collect(/*start*/_ match { case s: String => s }/*end*/)
}

//PartialFunction[String, NotInferedB]