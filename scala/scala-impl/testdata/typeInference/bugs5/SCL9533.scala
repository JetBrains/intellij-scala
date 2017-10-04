package something

import scala.annotation.StaticAnnotation

class enhance extends StaticAnnotation

object Test {
  @enhance class SomeClass { }
  val x = SomeClass.equals(SomeClass)
}

@enhance class SomeClas1s { }

object Test1 {
  val y = SomeClas1s.equals(SomeClas1s)
}

object Test2 {
  /*start*/(Test.x, Test1.y)/*end*/
}
//(Boolean, Boolean)