object Test {

  def simpleCall(i: Int): Int = i

  def genericCall[T](a: T): T = a

  def genericCall[T]: T = ???

  def main(args: Array[String]): Unit = {
    // simple calls
    simpleCall(5)
    genericCall(5)

    // qualified calls
    Math.atan2(1.3, 3.4)
    val _1: AnyRef = int2Integer(1)
    val _2: AnyRef = int2Integer(2)
    val _3: AnyRef = int2Integer(3)
    java.lang.String.format("%i %i %i", _1, _2, _3)
    java.lang.String.format("%i %i %i", Array(1, 2, 3))
    java.lang.String.format("%i %i %i", Array(1, 2, 3), Array(4, 5, 6))

    // method calls
    1.toString()
    1.toString().charAt(0)
    1.toString().charAt(0).hashCode()

    // reference calls
    1.toString
    1.toString.charAt(0)
    1.toString.charAt(0).hashCode

    // generic calls
    1.asInstanceOf[Int]
    1.isInstanceOf[Int]
    genericCall[Int](5)
    genericCall[String]

    // mixed calls
    1.toString().hashCode
    1.toString.hashCode()
    genericCall[Int].hashCode().toString
    simpleCall(5).isInstanceOf[Int]

    // this qualified
    this.simpleCall(5)
    this.genericCall[Int](5)

    // nested calls
    simpleCall(1.asInstanceOf[Int])
    genericCall[String](1.toString)
    genericCall[String](1.toString())
    genericCall[Int](simpleCall(5))
    simpleCall(genericCall[Int](5))
    simpleCall(genericCall[Int](1.hashCode()))
    simpleCall(genericCall[Int](1.asInstanceOf[Int]))
    simpleCall(simpleCall(5))
    genericCall[Int](genericCall[Int](5))

    // nested with qualified outers
    Test.simpleCall(1.asInstanceOf[Int])
    Test.genericCall[String](1.toString)
    Test.genericCall[String](1.toString())
    Test.genericCall[Int](simpleCall(5))
    Test.simpleCall(genericCall[Int](5))
    Test.simpleCall(genericCall[Int](1.hashCode()))
    Test.simpleCall(genericCall[Int](1.asInstanceOf[Int]))
    Test.simpleCall(simpleCall(5))
    Test.genericCall[Int](genericCall[Int](5))

    // nested with qualified inners
    genericCall[Int](Test.simpleCall(5))
    simpleCall(Test.genericCall[Int](5))
    simpleCall(Test.genericCall[Int](1.hashCode()))
    simpleCall(Test.genericCall[Int](1.asInstanceOf[Int]))
    simpleCall(Test.simpleCall(5))
    genericCall[Int](Test.genericCall[Int](5))

    // nested with qualified both
    Test.genericCall[Int](Test.simpleCall(5))
    Test.simpleCall(Test.genericCall[Int](5))
    Test.simpleCall(Test.genericCall[Int](1.hashCode()))
    Test.simpleCall(Test.genericCall[Int](1.asInstanceOf[Int]))
    Test.simpleCall(Test.simpleCall(5))
    Test.genericCall[Int](Test.genericCall[Int](5))

    // nested with this qualifiers
    this.simpleCall(genericCall[Int](5))
    simpleCall(this.genericCall[Int](5))
    this.simpleCall(this.genericCall[Int](5))
  }
}