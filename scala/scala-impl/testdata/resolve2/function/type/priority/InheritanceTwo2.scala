class Parent
class Child extends Parent

def f(a: Child, b: Parent) = {}
def f(a: Parent, b: Child) = {}

println(/* offset: 45 */f(new Child, new Parent))
println(/* offset: 77 */f(new Parent, new Child))