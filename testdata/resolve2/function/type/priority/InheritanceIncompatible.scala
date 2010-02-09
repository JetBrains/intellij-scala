class Parent
class Child extends Parent

def f(a: Child, b: Parent) = {}
def f(a: Parent, b: Child) = {}

println(/* offset: 76, applicable: false */f(new Parent, new Parent))
println(/* resolved: false */f(new Child, new Child))