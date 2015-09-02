package org.jetbrains.plugins.scala.debugger.smartStepInto

/**
 * @author Nikolay.Tropin
 */
class SmartStepIntoTest extends SmartStepIntoTestBase {
  def testChainedMethodsAndConstructor() {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def main(args: Array[String]) {
        |    val s = new A(11).id1().id2.asString  //stop here
        |  }
        |}
      """.stripMargin.trim()
    )
    addFileToProject("A.scala",
      """
        |class A(i: Int) {
        |
        |  val a = i
        |
        |  def id1() = {
        |    val x: A = this
        |    x
        |  }
        |
        |  def id2 = {
        |    val x: A = this
        |    x
        |  }
        |
        |  def asString = "A"
        |}
        |""".stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      checkSmartStepTargets("new A(int)", "id1()", "id2()", "asString()")
      checkSmartStepInto("new A(int)", "A.scala", "<init>", 1)
    }
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      checkSmartStepInto("id1()", "A.scala", "id1", 6)
    }
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      checkSmartStepInto("id2()", "A.scala", "id2", 11)
    }
  }

  def testInnerClassAndConstructor(): Unit = {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def main(args: Array[String]) {
        |    val s = new A(10).id1().asString //stop here
        |  }
        |
        |  class A(i: Int) {
        |
        |    def id1() = {
        |      val x: A = this
        |      x
        |    }
        |
        |    def asString = "A"
        |  }
        |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      checkSmartStepTargets("new A(int)", "id1()", "asString()")
      checkSmartStepInto("new A(int)", "Sample.scala", "<init>", 6)
    }
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      checkSmartStepInto("id1()", "Sample.scala", "id1", 9)
    }
  }

  def testInArguments(): Unit = {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |
        |  def foo(a: A, a1: A) = {}
        |
        |  def main(args: Array[String]) {
        |    val a = new A(2)
        |    foo(new A(1), a.id()) //stop here
        |  }
        |}
        |
        |class A(i: Int) {
        |
        |  def id() = {
        |    val x: A = this
        |    x
        |  }
        |
        |  def asString = "A"
        |}""".stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 6)
    runDebugger("Sample") {
      waitForBreakpoint()
      checkSmartStepTargets("foo(A, A)", "new A(int)", "id()")
      checkSmartStepInto("new A(int)", "Sample.scala", "<init>", 11)
    }
    addBreakpoint("Sample.scala", 6)
    runDebugger("Sample") {
      waitForBreakpoint()
      checkSmartStepInto("id()", "Sample.scala", "id", 14)
    }
  }

  def testInfixAndApply(): Unit = {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |
        |  def main(args: Array[String]) {
        |    val a = new A(2)
        |    a add A(1) //stop here
        |  }
        |}
        |
        |class A(i: Int) {
        |
        |  def add(a: A) = {
        |    "adding"
        |  }
        |}
        |
        |object A {
        |  def apply(i: Int) = new A(i)
        |}""".stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 4)
    runDebugger("Sample") {
      waitForBreakpoint()
      checkSmartStepTargets("add(A)", "A.apply(int)")
      checkSmartStepInto("add(A)", "Sample.scala", "add", 12)
    }
    addBreakpoint("Sample.scala", 4)
    runDebugger("Sample") {
      waitForBreakpoint()
      checkSmartStepInto("A.apply(int)", "Sample.scala", "apply", 17)
    }
  }

  def testPostfixAndUnapply(): Unit = {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |
        |  def main(args: Array[String]) {
        |    new A(1) match {
        |      case a @ A(1) => a foo //stop here
        |    }
        |  }
        |}
        |
        |class A(val i: Int) {
        |  def foo() = {}
        |
        |}
        |
        |object A {
        |  def unapply(a: A) = Some(a.i)
        |}""".stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 4)
    runDebugger("Sample") {
      waitForBreakpoint()
      checkSmartStepTargets("A.unapply(A)", "foo()")
      checkSmartStepInto("A.unapply(A)", "Sample.scala", "unapply", 16)
    }
//    runDebugger("Sample") {  //should work after cleaning up match statements
//      waitForBreakpoint()
//      checkSmartStepInto("foo()", "Sample.scala", "foo", 11)
//    }
  }

  def testAnonymousClassFromTrait(): Unit = {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |
        |  def execute(processor: Processor) = processor.execute()
        |
        |  def main(args: Array[String]) {
        |     execute(new Processor { //stop here
        |       val z = 1
        |
        |       override def execute(): Unit = {
        |         "aaa"
        |       }
        |     })
        |  }
        |}
        |
        |trait Processor {
        |  def execute()
        |}""".stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 5)
    runDebugger("Sample") {
      waitForBreakpoint()
      checkSmartStepTargets("execute(Processor)", "new Processor()", "new Processor.execute()")
      checkSmartStepInto("new Processor()", "Sample.scala", "<init>", 6)
    }
    addBreakpoint("Sample.scala", 5)
    runDebugger("Sample") {
      waitForBreakpoint()
      checkSmartStepInto("new Processor.execute()", "Sample.scala", "execute", 10)
    }
  }

  def testAnonymousClassFromClass(): Unit = {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |
        |  def execute(processor: Processor) = processor.execute()
        |
        |  def main(args: Array[String]) {
        |     execute(new Processor("aa") { //stop here
        |       val z = 1
        |
        |       override def execute(): Unit = {
        |         "aaa"
        |       }
        |     })
        |  }
        |}
        |
        |class Processor(s: String) {
        |  def execute(): Unit = {}
        |}""".stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 5)
    runDebugger("Sample") {
      waitForBreakpoint()
      checkSmartStepTargets("execute(Processor)", "new Processor()", "new Processor.execute()")
      checkSmartStepInto("new Processor()", "Sample.scala", "<init>", 6)
    }
    addBreakpoint("Sample.scala", 5)
    runDebugger("Sample") {
      waitForBreakpoint()
      checkSmartStepInto("new Processor.execute()", "Sample.scala", "execute", 10)
    }
  }

  def testByNameArgument(): Unit = {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |
        |  def inTryBlock(u: => String): Unit = {
        |    try {
        |      u
        |    }
        |    catch {
        |      case t: Throwable =>
        |    }
        |  }
        |
        |  def main(args: Array[String]) {
        |    inTryBlock { //stop here
        |      val s = "a"
        |      s + "aaa"
        |    }
        |  }
        |}""".stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 12)
    runDebugger("Sample") {
      waitForBreakpoint()
      checkSmartStepTargets("inTryBlock(String)", "u: => String")
      checkSmartStepInto("inTryBlock(String)", "Sample.scala", "inTryBlock", 5)
    }
    addBreakpoint("Sample.scala", 12)
    runDebugger("Sample") {
      waitForBreakpoint()
      checkSmartStepInto("u: => String", "Sample.scala", "apply", 14)
    }
  }

  def testLocalFunction(): Unit = {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |
        |  def main(args: Array[String]) {
        |    def foo(s: String): Unit = {
        |      println(s)
        |    }
        |
        |    foo("aaa") //stop here
        |  }
        |}""".stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 7)
    runDebugger("Sample") {
      waitForBreakpoint()
      checkSmartStepTargets("foo(String)")
      checkSmartStepInto("foo(String)", "Sample.scala", "foo$1", 5)
    }
  }

  def testImplicitConversion(): Unit = {
    addFileToProject("Sample.scala",
      """
        |import scala.language.implicitConversions
        |
        |object Sample {
        |
        |  implicit def string2Int(s: String): Int = Integer.valueOf(s)
        |
        |  def inc(i: Int): Int = {
        |    i + 1
        |  }
        |
        |  def main(args: Array[String]) {
        |    inc("1") //stop here
        |  }
        |}""".stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 11)
    runDebugger("Sample") {
      waitForBreakpoint()
      checkSmartStepTargets("inc(int)", "implicit string2Int(String)")
      checkSmartStepInto("implicit string2Int(String)", "Sample.scala", "string2Int", 5)
    }
  }

  def testImplicitClass(): Unit = {
    addFileToProject("Sample.scala",
      """
        |import scala.language.implicitConversions
        |
        |object Sample {
        |
        |  implicit class ObjectExt[T](v: T) {
        |    def toOption: Option[T] = Option(v)
        |  }
        |
        |  def main(args: Array[String]) {
        |    "aaa".charAt(1).toOption //stop here
        |  }
        |}""".stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 9)
    runDebugger("Sample") {
      waitForBreakpoint()
      checkSmartStepTargets("charAt(int)", "implicit toOption()")
      checkSmartStepInto("implicit toOption()", "Sample.scala", "toOption", 6)
    }
  }

  def testImplicitValueClass(): Unit = {
    addFileToProject("Sample.scala",
      """
        |import scala.language.implicitConversions
        |
        |object Sample {
        |
        |  implicit class ObjectExt[T](val v: T) extends AnyVal {
        |    def toOption: Option[T] = Option(v)
        |  }
        |
        |  def main(args: Array[String]) {
        |    "aaa".charAt(1).toOption //stop here
        |  }
        |}""".stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 9)
    runDebugger("Sample") {
      waitForBreakpoint()
      checkSmartStepTargets("charAt(int)", "implicit toOption()")
      checkSmartStepInto("implicit toOption()", "Sample.scala", "toOption$extension", 6)
    }
  }

  def testMethodValue(): Unit = {
    addFileToProject("Sample.scala",
    """
      |object Sample {
      |  def main(args: Array[String]): Unit = {
      |    val a = new A(Seq(1, 2, 3))
      |    a.update(incr(2) _).update(Sample.id[Int](_)).update(a.decr)
      |  }
      |
      |  def incr(i: Int)(j: Int): Int = i + j
      |
      |  def id[T](t: T) = t
      |
      |  class A(var seq: Seq[Int]) {
      |    def update(f: Int => Int) = {
      |      seq = seq.map(f)
      |      this
      |    }
      |    def decr(i: Int) = i - 1
      |  }
      |}
      |""".stripMargin.trim
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      checkSmartStepTargets("update(Function1<Object, Object>)", "incr(int, int)", "update(Function1<Object, Object>)", "id(T)", "update(Function1<Object, Object>)", "decr(int)")
      checkSmartStepInto("id(T)", "Sample.scala", "id", 9)
    }
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      checkSmartStepInto("decr(int)", "Sample.scala", "decr", 16)
    }
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      checkSmartStepInto("incr(int, int)", "Sample.scala", "incr", 7)
    }
  }

}