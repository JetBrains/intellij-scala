object O {
  private case class CC1
  private[O] case class CC2

  /* */CC1.getClass
  classOf[/* */CC1]

  /* */CC2.getClass
  classOf[/* */CC2]

  /* */O./* */CC1.getClass
  classOf[/* */O./* */CC1]

  /* */O./* */CC2.getClass
  classOf[/* */O./* */CC2]
}
