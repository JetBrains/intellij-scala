class Parent
class Child extends Parent

def f(a: Child) = {}
def f(a: Parent) = {}

println(/* offset: 45 */f(new Child))
println(/* offset: 66 */f(new Parent))