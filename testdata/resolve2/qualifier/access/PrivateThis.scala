object O {
  private def f1 {}
  private[this] def f2 {}

  /* */f1
  /* */f2
  /* */O./* */f1
  /* */O./* accessible: false */f2
}
