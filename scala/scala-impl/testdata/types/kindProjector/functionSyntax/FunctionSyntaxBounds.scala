class GrandParent
class Parent extends GrandParent
class Child extends Parent
def foo: /*start*/Lambda[`C >: Child <: GrandParent` => C]/*end*/
//({type Λ$[C >: Child <: GrandParent] = C})#Λ$