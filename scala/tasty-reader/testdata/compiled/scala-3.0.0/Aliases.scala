trait Aliases {
  object O {
    type Type = Int

    val value: Int = ???

    def method(x: Int): Unit = ???

    def repeatedParameter(xs: Int*): Unit = ???

    inline def inlineParameter(inline x: Int): Unit = ???
  }

  /**/export O.Type/*final type Type = O.Type*/

  /**/export O.value/*final val value: O.value.type = ???*/

  /**/export O.repeatedParameter/*final def repeatedParameter(xs: Int*): Unit = ???*/

  /**/export O.inlineParameter/*final inline def inlineParameter(inline x: Int): Unit = ???*/
}