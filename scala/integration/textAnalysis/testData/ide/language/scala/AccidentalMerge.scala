class C {
  /**
   * @param a<GRAMMAR_ERROR descr="UNLIKELY_OPENING_PUNCTUATION"><caret>:</GRAMMAR_ERROR>here is some English text producing an error. And here's another sentence.
   */
  def foo(a: Int): Unit = ()
}