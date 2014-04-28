object SCL4380 {

  class A {
    def f: PartialFunction[Any, Unit] = {
      case x: String => 1
    }
  }

  class B extends A {
    override def f = super.f orElse  {
      case z: Int => /*start*/1/*end*/ // error here
    }
  }

}
//Unit