class DebuggingQueryFactory(queryFactory: Any, log: String => Unit)

new DebuggingQueryFactory("", /*resolved: true*/println _)

