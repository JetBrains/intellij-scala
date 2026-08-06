object SCL2936 {
  object MyEnum extends Enumeration {
    val First = Value
  }

  class Test {
    def stuff(v: MyEnum.Value) {
      println(v.toString)
    }
    def main(args: Array[String]) {
      stuff(/*start*/MyEnum(1)/*end*/)
    }
  }
}
//SCL2936.MyEnum.Value