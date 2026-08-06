object SCL7502B {
  object literal extends Dynamic {
    def applyDynamicNamed(name: String)(
      fields: (String, Int)*): Object with Dynamic = sys.error("stub")
  }

  implicit def boolean2int(b : Boolean): Int = 123

  literal(foo = false, bar = /*start*/false/*end*/)
}
//Int