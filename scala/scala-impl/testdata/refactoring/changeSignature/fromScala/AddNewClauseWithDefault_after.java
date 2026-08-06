public class AddNewClauseWithDefaultJava extends AddNewClauseWithDefault {
    @Override
    public void foo(boolean b, int x, int y) {
        AddNewClauseWithDefault a = new AddNewClauseWithDefault();
        a.foo(a.foo$default$1(), 1, a.foo$default$3(a.foo$default$1()));


        super.foo(foo$default$1(), x, foo$default$3(foo$default$1()));
    }
}
