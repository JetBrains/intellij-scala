class Parent
class Child extends Parent

def f(a: Child, b: Child) = {}
def f(a: Parent, b: Parent) = {}

println(/* offset: 45 */f(new Child, new Child))
println(/* offset: 76 */f(new Parent, new Parent))