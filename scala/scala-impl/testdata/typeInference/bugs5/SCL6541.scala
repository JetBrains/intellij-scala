object SCL6541 {
  implicit class MyStringContext(val sc: StringContext) extends AnyVal {
    def my(args: Any*): String = {
      sc.checkLengths(args)
      val parts:Seq[String] = sc.parts //not red in IDEA
      val pi: Iterator[String] = /*start*/sc.parts.iterator/*end*/ //red in IDEA, it thinks parts is a String, not a Seq[String]
      val ai = args.iterator
      val bldr = new java.lang.StringBuilder(StringContext.treatEscapes(pi.next()))
      while (ai.hasNext) {
        bldr append ai.next
        bldr append StringContext.treatEscapes(pi.next())
      }
      bldr.toString
    }
  }
}
//Iterator[String]