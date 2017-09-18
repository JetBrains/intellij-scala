object SCL4093 {
  class IntellijScalaBug1
  {
    def theBug()
    {
      val index = new IntellijBugSample
      setIfNotNull(/*start*/index.id_=/*end*/, "THIS SHOULD WORK!") // this line is correct scala
    }

    /**
     * Helper which calls the passed in setter if value is not null.  Caller passes in the setter using the 'field_=' notation,
     * which as of this writing, intellij thinks is wrong, but actually compiles and works fine.
     */
    def setIfNotNull[T](setter: T => Unit, value: T)
    {
      if (value != null) setter(value)
    }
  }

  class IntellijBugSample
  {
    // ids
    var id: String = _
  }
}
//String => Unit