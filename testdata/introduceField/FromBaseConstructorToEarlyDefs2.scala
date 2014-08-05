/*initInDeclaration*/
class A(i: Int)
class Test extends {val x = 0} with A(/*start*/1/*end*/)
/*
/*initInDeclaration*/
class A(i: Int)
class Test extends {val x = 0; var i: Int = 1} with A(/*start*/i/*end*/)
*/