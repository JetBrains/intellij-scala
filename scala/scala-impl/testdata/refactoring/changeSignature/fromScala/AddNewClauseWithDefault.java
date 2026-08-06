public class AddNewClauseWithDefaultJava extends AddNewClauseWithDefault {
    @Override
    public void foo(int x) {
        AddNewClauseWithDefault a = new AddNewClauseWithDefault();
        a.foo(1);


        super.foo(x);
    }
}
