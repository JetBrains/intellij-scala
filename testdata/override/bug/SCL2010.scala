override doSmth
package test

class Parent {
  def doSmth(smth: => String) {}
}

class Child extends Parent {
 <caret>
}<end>
package test

class Parent {
  def doSmth(smth: => String) {}
}

class Child extends Parent {
  override def doSmth(smth: => String) {}
}