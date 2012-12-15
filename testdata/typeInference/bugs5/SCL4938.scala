import scala.concurrent.duration._

class DurationTest {
  //Should be triggering DurationInt implicit class inside duration package object
  val timeout: FiniteDuration = /*start*/1 millis span/*end*///This is valid, millis and span highlighted in red.
}
//DurationConversions.spanConvert.R