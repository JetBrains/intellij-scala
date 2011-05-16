class Z[Q, R]

val x: ((Z[Q, R]) forSome {type Q; type X}) forSome {type R}

/*start*/x/*end*/
//(Z[Q, R]) forSome {type Q; type R}