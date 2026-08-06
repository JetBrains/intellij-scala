import scala.language.implicitConversions

/**
  * Created on 9/14/15.
  */
object Test {
  def main(args: Array[String]) {
    val timeoutDuration = 20.seconds
    implicit val timeout = timeoutDuration
    <ref>foo(timeoutDuration)
  }

  def foo(l: Int) = ???
  def foo(l: FiniteDuration) = ???

  implicit class DurationInt(val n: Int) {
    def seconds: FiniteDuration = ???
  }

  class FiniteDuration
}