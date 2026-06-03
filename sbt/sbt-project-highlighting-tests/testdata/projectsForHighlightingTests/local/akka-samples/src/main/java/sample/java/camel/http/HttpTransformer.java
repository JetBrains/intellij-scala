package sample.java.camel.http;

import akka.actor.Status;
import akka.actor.AbstractActor;
import akka.camel.CamelMessage;
import akka.dispatch.Mapper;

public class HttpTransformer extends AbstractActor {
  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .match(CamelMessage.class, camelMessage -> {
        CamelMessage replacedMessage = camelMessage.mapBody(new Mapper<Object, String>() {
          @Override
          public String apply(Object body) {
            String text = new String((byte[]) body);
            return text.replaceAll("Akka ", "AKKA ");
          }
        });
        sender().tell(replacedMessage, self());
      })
      .match(Status.Failure.class, message -> {
        sender().tell(message, self());
      })
      .build();
  }
}
