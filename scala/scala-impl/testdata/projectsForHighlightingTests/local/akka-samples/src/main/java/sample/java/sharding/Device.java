package sample.java.sharding;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import akka.actor.*;
import akka.event.*;

public class Device extends AbstractActor {

  private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  private List<Double> temperatures = new ArrayList<Double>();

  public static class RecordTemperature implements Serializable {
    public final Integer deviceId;
    public final Double temperature;

    public RecordTemperature(Integer deviceId, Double temperature) {
      this.deviceId = deviceId;
      this.temperature = temperature;
    }

    @Override
    public String toString() {
      return "RecordTemperature(" + deviceId + ", " + temperature + ")";
    }
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .match(RecordTemperature.class, r -> {
        temperatures.add(r.temperature);
        log.info("Recording temperature {} for device {}, average is {} after {} readings",
          r.temperature, r.deviceId, sum(temperatures) / temperatures.size(), temperatures.size());
      })
      .build();
  }

  private Double sum(List<Double> doubles) {
    Double result = 0.0;
    for (Double d : doubles) {
      result += d;
    }
    return result;
  }
}
