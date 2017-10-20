trait Red

case class Apple(colour: String)

sealed class AugmentedApple(val apple: Apple) {
  def turnGreen: Apple = Apple("green")
}

object HelloWorld {
  def foo1() {
    implicit def appleToAugmentedApple(apple: Apple)(implicit ev: apple.type <:< Red): AugmentedApple = {
      new AugmentedApple(apple)
    }


    val a1 = new Apple("red") with Red
    a1.<ref>turnGreen // will not highlight
  }


  //  def foo2() {
  //    implicit def appleToAugmentedApple(apple: Apple with Red): AugmentedApple = {
  //      new AugmentedApple(apple)
  //    }
  //
  //    val a1 = new Apple("red") with Red
  //    a1.turnGreen // ok
  //  }
}
