implicit def pimp(a: Any): { def pimped: Int} = new {def pimped = 0}
/*start*/new{}.pimped/*end*/
//Int