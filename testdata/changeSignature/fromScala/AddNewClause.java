public class AddNewClauseJava extends AddNewClause {
    @Override
    public void foo(int x) {
        AddNewClause a = new AddNewClause();
        a.foo(1);


        super.foo(x);
    }
}
