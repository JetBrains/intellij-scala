class Parent
class Child extends Parent

def f(a: Child) = {}

println(/* applicable: false */f(new Parent))