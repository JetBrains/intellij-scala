package sample.sharding

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random

import akka.actor._
import akka.cluster.sharding._

object Devices {
  // Update a random device
  case object UpdateDevice
}

class Devices extends Actor with ActorLogging {
  import Devices._

  private val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg @ Device.RecordTemperature(id, _) => (id.toString, msg)
  }

  private val numberOfShards = 100

  private val extractShardId: ShardRegion.ExtractShardId = {
    case Device.RecordTemperature(id, _) => (id % numberOfShards).toString
    // Needed if you want to use 'remember entities':
    //case ShardRegion.StartEntity(id) => (id.toLong % numberOfShards).toString
  }

  val deviceRegion: ActorRef = ClusterSharding(context.system).start(
      typeName = "Device",
      entityProps = Props[Device],
      settings = ClusterShardingSettings(context.system),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId)

  val random = new Random()
  val numberOfDevices = 50

  implicit val ec: ExecutionContext = context.dispatcher
  context.system.scheduler.schedule(10.seconds, 1.second, self, UpdateDevice)

  def receive = {
    case UpdateDevice =>
      val deviceId = random.nextInt(numberOfDevices)
      val temperature = 5 + 30 * random.nextDouble()
      val msg = Device.RecordTemperature(deviceId, temperature)
      log.info(s"Sending $msg");
      deviceRegion ! msg
  }
}
