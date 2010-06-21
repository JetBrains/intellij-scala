def m(x : String)(implicit anyToString: (Any => String) = (p: Any) => "converted") {
	/*start*/2/*end*/.substring(1)
}
/*
Seq(any2ArrowAssoc,
    any2Ensuring,
    any2stringadd,
    anyToString,
    conforms,
    int2Integer,
    int2double,
    int2float,
    int2long,
    intWrapper),
Some(anyToString)
*/