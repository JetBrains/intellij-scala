trait Aliases {
  object O {
    type Type = Int

    val value: Int = ???

    def method(xs: Int*): Unit = ???
  }

  /**/export O.Type/*final type Type = O.Type*/

  /**/export O.value/*final val value: O.value = ???*/

  /**/export O.method/*final def method(xs: Int*): Unit = ???*/
}