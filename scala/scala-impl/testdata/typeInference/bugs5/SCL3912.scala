object CurrencyWithAbstractType{

  trait AbstractCurrency{
    type Currency <: AbstractCurrency
    def value : Double

    def make(d:Double) : Currency
    def +(x: AbstractCurrency) = make(x.value + value)
  }

  class USD(val value: Double) extends AbstractCurrency {
    type Currency = USD
    def make(d: Double) = new USD(d)
  }

  def plus[T <: AbstractCurrency](c1: T,  c2:T) : T#Currency = /*start*/c1  + c2/*end*/
  val x : USD = plus(new USD(100),new USD(200))
}
//c1.Currency