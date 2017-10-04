object O1 {
  private object O2 {
    def f {}
  }
}

O1./* accessible: false */O2.f
