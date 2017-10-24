trait HasApply { def apply: String }
var a: () => HasApply = _
/*start*/a.apply()/*end*/
//HasApply