object SCL10091 {

  implicit class ExampleOps(val ec: StringContext) {

    object d extends Dynamic {
      def applyDynamic(method: String)(args: Any*) = impl(method, args)
    }

    def impl(method: String, args: Any*) =
      s"$method / ${ec.parts.mkString(", ") } / ${args.mkString(", ")}"
  }

  val a = 'a'
  val t = true

  println(<ref>d"1 $a 2 $t 3")
}