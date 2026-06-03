def /*caret*/x(a: Int, b: Int) = a +: b
x(2, 4) + 3
3 + x(2, 4)
x(2, 4) op 4
4 op x(2, 4)
/*
(2 +: 4) + 3
3 + (2 +: 4)
2 +: 4 op 4
4 op 2 +: 4
*/