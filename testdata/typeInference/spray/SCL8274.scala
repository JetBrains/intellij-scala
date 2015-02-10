import spray.routing.HttpService

trait SCL8274 extends HttpService {
  val testRoutes =
    post {
      /*start*/path("foo" ) {
        complete("foo")
      }/*end*/
    }
}
//routing.Route