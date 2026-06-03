package hello

import utest._

object HelloTests extends TestSuite {
  val tests = Tests {
    'test2 - {
      1
    }
    'test3 - {
      assert(Hello.foo == 2)
    }
  }
}
