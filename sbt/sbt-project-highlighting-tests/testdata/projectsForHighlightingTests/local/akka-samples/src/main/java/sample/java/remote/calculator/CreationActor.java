package sample.java.remote.calculator;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.AbstractActor;

public class CreationActor extends AbstractActor {

  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .match(Op.MathOp.class, message -> {
        ActorRef calculator = getContext().actorOf(
          Props.create(CalculatorActor.class));
        calculator.tell(message, self());
      })
      .match(Op.MultiplicationResult.class, result -> {
        System.out.printf("Mul result: %d * %d = %d\n", result.getN1(),
          result.getN2(), result.getResult());
        getContext().stop(sender());
      })
      .match(Op.DivisionResult.class, result -> {
        System.out.printf("Div result: %.0f / %d = %.2f\n", result.getN1(),
          result.getN2(), result.getResult());
        getContext().stop(sender());
      })
      .build();
  }
}
