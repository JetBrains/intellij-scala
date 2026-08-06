package sample.java.camel.route;

import akka.actor.ActorRef;
import akka.actor.AbstractActor;
import akka.camel.CamelMessage;
import akka.dispatch.Mapper;

public class RouteTransformer extends AbstractActor {
  private ActorRef producer;

  public RouteTransformer(ActorRef producer) {
    this.producer = producer;
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .match(CamelMessage.class, camelMessage -> {
        // example: transform message body "foo" to "- foo -" and forward result
        // to producer
        CamelMessage transformedMessage = camelMessage.mapBody(new Mapper<String, String>() {
          @Override
          public String apply(String body) {
            return String.format("- %s -", body);
          }
        });
        producer.forward(transformedMessage, getContext());
      })
      .build();
  }
}
