object Holder {

  object ActorMaterializer {

    def apply()(implicit context: ActorRefFactory): ActorMaterializer = ???
  }

  trait Materializer

  abstract class ActorMaterializer extends Materializer

  class ActorMaterializerSettings

  trait ActorRefFactory

  case class ActorSystem(s: String) extends ActorRefFactory

  class Route

  class Flow

  implicit def route2HandlerFlow(route: Route)
                                (implicit routingSettings: RoutingSettings, materializer: Materializer): Flow = ???

  trait SettingsCompanion[T] {
    implicit def default(implicit system: ActorRefFactory): T = ???
  }

  object RoutingSettings extends SettingsCompanion[RoutingSettings]

  abstract class RoutingSettings
}

object Test {

  import scala.concurrent.ExecutionContext
  import Holder._

  def bindAndHandle(handler: Flow, interface: String, port: Int)
                   (implicit fm: Materializer): Any = ???

  def main(args: Array[String]) {
    implicit val system = ActorSystem("foo")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext: ExecutionContext = ???

    val route: Route = ???

    val bindingFuture = bindAndHandle(/*start*/route/*end*/, "localhost", 8080)
  }
}
//Holder.Flow