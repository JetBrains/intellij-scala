package sample.java.sharding;

import akka.actor.ActorSystem;
import akka.actor.Props;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class ShardingApp {

  public static void main(String[] args) {
    if (args.length == 0)
      startup(new String[] { "2551", "2552", "0" });
    else
      startup(args);
  }

  public static void startup(String[] ports) {
    for (String port : ports) {
      // Override the configuration of the port
      Config config = ConfigFactory.parseString(
          "akka.remote.netty.tcp.port=" + port).withFallback(
          ConfigFactory.load());

      // Create an Akka system
      ActorSystem system = ActorSystem.create("ShardingSystem", config);

      // Create an actor that starts the sharding and sends random messages
      system.actorOf(Props.create(Devices.class));
    }
  }
}