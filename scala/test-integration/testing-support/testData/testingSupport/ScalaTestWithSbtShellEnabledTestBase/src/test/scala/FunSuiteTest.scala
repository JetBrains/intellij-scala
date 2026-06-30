import org.scalatest._ ; import org.scalatest.funsuite._

class FunSuiteTest extends AnyFunSuite {

  test("should not run other tests") { }

  test("should run single test") { }

  test("tagged", FunSuiteTag) {}
}

object FunSuiteTag extends Tag("MyTag")
