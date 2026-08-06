object AssignmentCall {
  class A {
    def +++(x: Int) = new A
  }

  var g = new A
  g <ref>+++= 23
}