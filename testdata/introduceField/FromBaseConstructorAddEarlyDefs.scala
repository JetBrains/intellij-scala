/*initInDeclaration*/
class A(i: Int)
class Test extends A(/*start*/1/*end*/)
/*
/*initInDeclaration*/
class A(i: Int)
class Test extends {
  var i: Int = 1
} with A(/*start*/i/*end*/)
*/