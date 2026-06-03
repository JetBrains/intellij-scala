import spray.routing.HttpService

trait SprayRouting extends HttpService {
  val testRoutes =
    post {
      /*start*/path("foo" ) {
        complete("foo")
      }/*end*/
    }
}
//routing.Route