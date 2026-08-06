object O {
  private def f1 {}
  private[O] def f2 {}

  /* */f1
  /* */f2
  /* */O./* */f1
  /* */O./* */f2
}
