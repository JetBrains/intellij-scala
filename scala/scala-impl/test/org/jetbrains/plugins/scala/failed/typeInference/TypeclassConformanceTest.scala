import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase
import org.junit.experimental.categories.Category

/**
  * @author Anton Yalyshev
  * @since 06.07.2016.
  */
@Category(Array(classOf[PerfCycleTests]))
class TypeclassConformanceTest extends TypeConformanceTestBase {

  def testSCL10491(): Unit = doTest(
    s"""
       |object SingletonClass {
       |  val x = "x"
       |  trait SingletonS[T] {
       |    type A
       |    def create: A
       |  }
       |
       |  implicit val ximp: SingletonS[x.type] { type A = Int } = new SingletonS[x.type] {
       |    override type A = Int
       |    override def create: Int = 1
       |  }
       |
       |  def withInstance(y: String)(implicit ev: SingletonS[y.type]): ev.A = ev.create
       |  ${caretMarker}val z: Int = withInstance(x)
       |}
       |//true
       """.stripMargin)

  def testSCL10850(): Unit = doTest(
    s"""
       |  trait Foo[A] {
       |    type B
       |    def apply(a: A): B
       |  }
       |
       |  implicit val intFoo = new Foo[Int] {
       |    override type B = Double
       |    override def apply(a: Int): B = a.toDouble
       |  }
       |
       |  def fooId[A] = new Foo[A] {
       |    override type B = A
       |    override def apply(a: A): A = a
       |  }
       |
       |  def foo[T](t: T)(implicit f: Foo[T] = fooId[T]): f.B = f(t)
       |
       |  ${caretMarker}val test: String = foo("Some String")
       |//true
       """.stripMargin)
}
