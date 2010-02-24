class Parent
class Child extends Parent

def f(a: Child) = {}

println(/* valid: false */f(new Parent))