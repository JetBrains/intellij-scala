object ThisType extends ThisType

class ThisType {
  type Ambig = scala.AbstractMethodError

  object InnerObj

  class Inner /*start*/{
    type Ambig = scala.ArrayIndexOutOfBoundsException

    type typeA = scala.ArrayIndexOutOfBoundsException

    val ambig1: this.type#Ambig = ???
    val ambig2: this.Ambig = ???
    val ambig3: ThisType.this.Ambig = ???
    val ambig4: ThisType.this.type#Ambig = ???
    val ambig5: ThisType.Ambig = ???
    val ambig6: ThisType.type#Ambig = ???
    val ambig7: ThisType.this.Inner#Ambig = ???
    val ambig8: ThisType.Inner#Ambig = ???

    val inner1: ThisType.this.type#Inner = new Inner
    val inner2: ThisType.this.Inner = new Inner
    val inner3: ThisType#Inner = new Inner

    val other = new ThisType
    val inner4: other.type#Inner = new other.Inner
    val inner5: other.Inner = new other.Inner

    val thisType: this.type = this

    val typeA1: this.typeA = ???
    val typeA2: this.type#typeA = ???
    val typeA3: Inner#typeA = ???

    val innerObj: ThisType.this.InnerObj.type = InnerObj

    val outerthis: ThisType.this.type = ThisType.this
  }/*end*/
}

/*
object ThisType extends ThisType

class ThisType {
  type Ambig = scala.AbstractMethodError

  object InnerObj

  class Inner {
    type Ambig = ArrayIndexOutOfBoundsException

    type typeA = ArrayIndexOutOfBoundsException

    val ambig1: Ambig = ???
    val ambig2: Ambig = ???
    val ambig3: ThisType.this.Ambig = ???
    val ambig4: ThisType.this.Ambig = ???
    val ambig5: ThisType.Ambig = ???
    val ambig6: ThisType.Ambig = ???
    val ambig7: Inner#Ambig = ???
    val ambig8: ThisType.Inner#Ambig = ???

    val inner1: Inner = new Inner
    val inner2: Inner = new Inner
    val inner3: ThisType#Inner = new Inner

    val other = new ThisType
    val inner4: other.Inner = new other.Inner
    val inner5: other.Inner = new other.Inner

    val thisType: this.type = this

    val typeA1: typeA = ???
    val typeA2: typeA = ???
    val typeA3: Inner#typeA = ???

    val innerObj: InnerObj.type = InnerObj

    val outerthis: ThisType.this.type = ThisType.this
  }
}
*/