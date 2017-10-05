public class RemoveClause extends RemoveClauseConstructor {

    public RemoveClause(int i, boolean b) {
        super(i, b);
    }

    public void foo() {
        RemoveClauseConstructor c = new RemoveClauseConstructor(1, true);
    }
}
