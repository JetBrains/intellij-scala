type op = PartialFunction[Char, (Int, Int) => Int]

val operators:List[op] = List(
{case '+' => (x ,y) => x+y},
{case '-' => (x,y) => /*start*/x-y/*end*/}
)
//Int