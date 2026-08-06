package myPackage3

import org.scalatest._ ; import org.scalatest.funsuite._

class NestedStepsSuite extends Suites(
  new StepSuiteNotDiscoverable1,
  new StepSuiteNotDiscoverable2,
  new StepSuiteDiscoverable
)
@DoNotDiscover
class StepSuiteNotDiscoverable1 extends AnyFunSuite {
  test("test1.1") { println("1.1" ) }
  test("test1.2") { println("1.2" ) }
}
@DoNotDiscover
class StepSuiteNotDiscoverable2 extends AnyFunSuite {
  test("test2.1") { println("2.1" ) }
}
class StepSuiteDiscoverable extends AnyFunSuite {
  test("test3.1") { println("3.1" ) }
}
