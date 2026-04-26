package sample.java.cluster.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import scala.concurrent.duration.Duration;
import akka.actor.ActorRef;
import akka.actor.ReceiveTimeout;
import akka.actor.AbstractActor;

public class StatsAggregator extends AbstractActor {

  final int expectedResults;
  final ActorRef replyTo;
  final List<Integer> results = new ArrayList<>();

  public StatsAggregator(int expectedResults, ActorRef replyTo) {
    this.expectedResults = expectedResults;
    this.replyTo = replyTo;
  }

  @Override
  public void preStart() {
    getContext().setReceiveTimeout(Duration.create(3, TimeUnit.SECONDS));
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .match(Integer.class, wordCount -> {
        results.add(wordCount);
        if (results.size() == expectedResults) {
          int sum = 0;
          for (int c : results)
            sum += c;
          double meanWordLength = ((double) sum) / results.size();
          replyTo.tell(new StatsMessages.StatsResult(meanWordLength), self());
          getContext().stop(self());
        }
      })
      .match(ReceiveTimeout.class, x -> {
        replyTo.tell(new StatsMessages.JobFailed("Service unavailable, try again later"),
          self());
        getContext().stop(self());
      })
      .build();
  }

}
