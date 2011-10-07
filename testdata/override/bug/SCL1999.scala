implement m
package test

trait Parent {
  def m(p: T forSome {type T <: Number})
}

class Child extends Parent {
  <caret>
}<end>
package test

trait Parent {
  def m(p: T forSome {type T <: Number})
}

class Child extends Parent {
  def m(p: (T) forSome {type T <: Number}) = null
}