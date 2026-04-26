package sample.java.remote.calculator;

import akka.actor.AbstractActor;

public class CalculatorActor extends AbstractActor {
  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .match(Op.Add.class, add -> {
        System.out.println("Calculating " + add.getN1() + " + " + add.getN2());
        Op.AddResult result = new Op.AddResult(add.getN1(), add.getN2(),
          add.getN1() + add.getN2());
        sender().tell(result, self());
      })
      .match(Op.Subtract.class, subtract -> {
        System.out.println("Calculating " + subtract.getN1() + " - "
          + subtract.getN2());
        Op.SubtractResult result = new Op.SubtractResult(subtract.getN1(),
          subtract.getN2(), subtract.getN1() - subtract.getN2());
        sender().tell(result, self());
      })
      .match(Op.Multiply.class, multiply -> {
        System.out.println("Calculating " + multiply.getN1() + " * "
          + multiply.getN2());
        Op.MultiplicationResult result = new Op.MultiplicationResult(
          multiply.getN1(), multiply.getN2(), multiply.getN1()
          * multiply.getN2());
        sender().tell(result, self());
      })
      .match(Op.Divide.class, divide -> {
        System.out.println("Calculating " + divide.getN1() + " / "
          + divide.getN2());
        Op.DivisionResult result = new Op.DivisionResult(divide.getN1(),
          divide.getN2(), divide.getN1() / divide.getN2());
        sender().tell(result, self());
      })
      .build();
  }
}
