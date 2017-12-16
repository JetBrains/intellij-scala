package sample.sharding

import akka.actor._

/**
 * This is just an example: cluster sharding would be overkill for just keeping a small amount of data,
 * but becomes useful when you have a collection of 'heavy' actors (in terms of processing or state)
 * so you need to distribute them across several nodes.
 */
object Device {
  case class RecordTemperature(deviceId: Int, temperature: Double)
}
class Device extends Actor with ActorLogging {
  import Device._

  override def receive = counting(Nil)

  def counting(values: List[Double]): Receive = {
    case RecordTemperature(id, temp) =>
      val temperatures = temp :: values
      log.info(s"Recording temperature $temp for device $id, average is ${temperatures.sum / temperatures.size} after ${temperatures.size} readings");
      context.become(counting(temperatures))
  }
}
