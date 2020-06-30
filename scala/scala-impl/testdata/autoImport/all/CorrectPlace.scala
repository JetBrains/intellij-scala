object Times {
  implicit class IntTimes(val n: Int) {
    def times[U](f: => U): Unit = {
      @/*ref*/tailrec
      def loop(current: Int): Unit = {
        if (current > 0) {
          f
          loop(current - 1)
        }
      }
      loop(n)
    }
  }
}
import Times._
object TimesTest {
  5 times println("hi!")
}
/*
import scala.annotation.tailrec

object Times {
  implicit class IntTimes(val n: Int) {
    def times[U](f: => U): Unit = {
      @/*ref*/tailrec
      def loop(current: Int): Unit = {
        if (current > 0) {
          f
          loop(current - 1)
        }
      }
      loop(n)
    }
  }
}
import Times._
object TimesTest {
  5 times println("hi!")
}
*/