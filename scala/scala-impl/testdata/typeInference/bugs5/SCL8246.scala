abstract class Data {
  type Self <: Data
  def :=(that: Self)
}
class Flow[T <: Data](val gen: T){
  def <<(that: Flow[gen.Self]) = {
    this.gen := /*start*/that.gen/*end*/         //that.gen  is marked as read (type mismatch, expected Flow.this.gen.Self, actual that.gen.Self
  }
}
//Flow.this.gen.Self