class Parent
class Child extends Parent

def f(a: Parent) = {}
def f(a: Child) = {}

println(/* offset: 67 */f(new Child))
println(/* offset: 45 */f(new Parent))