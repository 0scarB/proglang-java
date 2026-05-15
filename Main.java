class ParseError extends Exception {
    public ParseError(String msg) {
        super(msg);
    }
}
class InterpreterError extends Exception {
    public InterpreterError(String msg) {
        super(msg);
    }
}

enum TokenType {
    EOF,
    INT,
    ADD,
    SUB,
    MUL,
    DIV,
    BAND,
    BOR,
    LAND,
    LOR,
    LPAREN,
    RPAREN,
    SEMI,
    PRINT,
}
class Token {
    public final TokenType type;
    public        Token(TokenType type) { this.type = type; }
    public int    getIntVal()           { return 0; }
    public String toString()            { return type.name(); }
}
class IntToken extends Token {
    private final int val;
    public IntToken(TokenType type, int val) {
        super(type);
        this.val = val;
    }
    @Override
    public int getIntVal() { return this.val; }
    @Override
    public String toString() { return type.name() + "(" + val + ")"; }
}
class Tokenizer {
    private final String src;
    private int pos = 0;

    public Tokenizer(String src) { this.src = src; }

    private char currChar() { return pos < src.length() ? src.charAt(pos) : 0; }
    private char nextChar() {
        ++pos;
        return currChar();
    }

    public Token next() throws ParseError {
        char c = currChar();

        // Skip whitespace
        while (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
            c = nextChar();
        }

        switch (c) {
            case 0:
                return new Token(TokenType.EOF);
            case '0':
            case '1': case '2': case '3':
            case '4': case '5': case '6':
            case '7': case '8': case '9':
                int x = c - '0';
                for (;;) {
                    c = nextChar();
                    if (c < '0' || '9' < c) break;
                    x = 10*x + (c - '0');
                }
                return new IntToken(TokenType.INT, x);
            case '+':
                ++pos;
                return new Token(TokenType.ADD);
            case '-':
                ++pos;
                return new Token(TokenType.SUB);
            case '*':
                ++pos;
                return new Token(TokenType.MUL);
            case '/':
                ++pos;
                return new Token(TokenType.DIV);
            case '(':
                ++pos;
                return new Token(TokenType.LPAREN);
            case ')':
                ++pos;
                return new Token(TokenType.RPAREN);
            case ';':
                ++pos;
                return new Token(TokenType.SEMI);
            case '&':
                ++pos;
                if (currChar() == '&') {
                    ++pos;
                    return new Token(TokenType.LAND);
                }
                return new Token(TokenType.BAND);
            case '|':
                ++pos;
                if (currChar() == '|') {
                    ++pos;
                    return new Token(TokenType.LOR);
                }
                return new Token(TokenType.BOR);
            default:
                int identifierStart = pos;
                int identifierStop  = pos;
                while (identifierStop < src.length()) {
                    char c2 = src.charAt(identifierStop);
                    if (!(  ('a' <= c2 && c2 <= 'z') ||
                            ('A' <= c2 && c2 <= 'Z') ||
                            ((identifierStop != identifierStart) && ('0' <= c2 && c2 <= '9')) ||
                            c2 == '_')
                    ) break;
                    ++identifierStop;
                }
                if (identifierStop != identifierStart) {
                    pos = identifierStop;
                    String identifier = src.substring(identifierStart, identifierStop);
                    if (identifier.equals("print")) {
                        return new Token(TokenType.PRINT);
                    } else {
                        throw new ParseError("Other identifiers not handled!");
                    }
                }
                throw new ParseError("Unexpected character '" + c + "'!");
        }
    }

    public Token peek() throws ParseError {
        int posRollback = pos;
        Token tok = next();
        pos = posRollback;
        return tok;
    }
}

interface Expr {}
record IntVal(int val) implements Expr {
    public String toString() {
        return "INT(" + val + ")";
    }
}
enum BinOp {
    ADD,
    SUB,
    MUL,
    DIV,
    BAND,
    BOR,
    LAND,
    LOR,
}
record BinExpr(BinOp op, Expr lhs, Expr rhs) implements Expr {
    public String toString() {
        return op.name() + "(" + lhs + " " + rhs + ")";
    }
}
class Stmt {
    public Stmt next = null;
}
class SingleExprStmt extends Stmt {
    public Expr expr;
}
class PrintStmt extends SingleExprStmt {
    public PrintStmt(Expr expr) { this.expr = expr; }
    public String toString() { return "PRINT(" + expr + ")"; }
}
class ExprStmt extends SingleExprStmt {
    public ExprStmt(Expr expr) { this.expr = expr; }
    public String toString() { return "EXPR_STMT(" + expr + ")"; }
}
class Program {
    public Stmt firstStmt = null;
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Stmt stmt = firstStmt; stmt != null; stmt = stmt.next) {
            sb.append(stmt.toString());
            sb.append('\n');
        }
        return sb.toString();
    }
}
class Parser {
    private final Tokenizer tokenizer;

    public Parser(String src) {
        tokenizer = new Tokenizer(src);
    }

    public Expr parseExpr() throws ParseError {
        Token tok = tokenizer.next();

        Expr innerExpr;
        if (tok.type == TokenType.INT) {
            innerExpr = new IntVal(tok.getIntVal());
        } else if (tok.type == TokenType.LPAREN) {
            innerExpr = parseExpr();
            tok = tokenizer.next();
            if (tok.type != TokenType.RPAREN) {
                throw new ParseError(
                    "Expression not closed with matching right parenthesis!");
            }
        } else {
            throw new ParseError("Unexpected start of expression!");
        }

        tok = tokenizer.peek();
        if (tok.type == TokenType.RPAREN ||
            tok.type == TokenType.SEMI   ||
            tok.type == TokenType.EOF
        ) {
            return innerExpr;
        }

        Expr lhs = innerExpr;
        tok = tokenizer.next();
        BinOp op = switch (tok.type) {
            case ADD  -> BinOp.ADD;
            case SUB  -> BinOp.SUB;
            case MUL  -> BinOp.MUL;
            case DIV  -> BinOp.DIV;
            case LAND -> BinOp.LAND;
            case LOR  -> BinOp.LOR;
            case BAND -> BinOp.BAND;
            case BOR  -> BinOp.BOR;
            default ->
                throw new ParseError(
                    "Expected binary-operator after expression left-hand-side!");
        };
        Expr rhs = parseExpr();
        return new BinExpr(op, lhs, rhs);
    }

    public Stmt parseStmt() throws ParseError {
        Token tok = tokenizer.peek();
        Stmt stmt;
        if (tok.type == TokenType.PRINT) {
            tokenizer.next();
            Expr expr = parseExpr();
            stmt = new PrintStmt(expr);
        } else {
            Expr expr = parseExpr();
            stmt = new ExprStmt(expr);
        }
        tok = tokenizer.next();
        if (tok.type != TokenType.SEMI)
            throw new ParseError("Expected semicolon ';' at end of statement!");
        return stmt;
    }

    public Program parse() throws ParseError {
        Program prog = new Program();
        Stmt stmt = null;
        while (tokenizer.peek().type != TokenType.EOF) {
            Stmt prevStmt = stmt;
            stmt          = parseStmt();
            if (prog.firstStmt == null) prog.firstStmt = stmt;
            if (prevStmt       != null) prevStmt.next  = stmt;
        }
        return prog;
    }
}

class Interpreter {
    private final Parser parser;
    private final StringBuilder output;

    public Interpreter(String src) {
        parser = new Parser(src);
        output = new StringBuilder();
    }

    private boolean intToBool(int x) { return x != 0; }
    private int boolToInt(boolean b) { return b ? 1 : 0; }

    private int evalExpr(Expr expr) throws InterpreterError {
        if (expr instanceof IntVal(int val)) return val;

        if (expr instanceof BinExpr(BinOp op, Expr lhs, Expr rhs)) {
            int a = evalExpr(lhs);
            int b = evalExpr(rhs);
            return switch (op) {
                case ADD  -> a + b;
                case SUB  -> a - b;
                case MUL  -> a * b;
                case BAND -> a & b;
                case BOR  -> a | b;
                case LAND -> boolToInt(intToBool(a) && intToBool(b));
                case LOR  -> boolToInt(intToBool(a) || intToBool(b));
                case DIV  -> {
                    if (b == 0)
                        throw new InterpreterError("Division by zero!");
                    yield a / b;
                }
            };
        }

        throw new InterpreterError("Unexpected expression type!");
    }

    public String interpret() throws ParseError, InterpreterError {
        Program prog = parser.parse();
        for (Stmt stmt = prog.firstStmt; stmt != null; stmt = stmt.next) {
            switch (stmt) {
                case PrintStmt printStmt -> {
                    int exprResult = evalExpr(printStmt.expr);
                    output.append(exprResult);
                    output.append('\n');
                }
                case ExprStmt exprStmt ->
                    evalExpr(exprStmt.expr);
                default ->
                    throw new InterpreterError("Unexpected statement type!");
            }
        }
        return output.toString();
    }
}

public class Main {
    static void main() throws ParseError, InterpreterError {
        //Tokenizer tokenizer = new Tokenizer("1 + 2");
        //for (;;) {
        //    Token tok = tokenizer.next();
        //    System.out.println(tok);
        //    if (tok.type == TokenType.EOF) break;
        //}

        Parser parser = new Parser("1 + 2;");
        System.out.println(parser.parse());
        parser = new Parser("1 + 2*3;");
        System.out.println(parser.parse());
        parser = new Parser("(1 + 2) * 3;");
        System.out.println(parser.parse());
        parser = new Parser("(1 + (2/3)) * 4;");
        System.out.println(parser.parse());
        parser = new Parser("0 || 2;");
        System.out.println(parser.parse());
        parser = new Parser("print 1 + 2;");
        System.out.println(parser.parse());

        assert new Interpreter("print 1 + 2;").interpret().equals("3\n");
        assert new Interpreter("print 1 + (2*3);").interpret().equals("7\n");
        assert new Interpreter("print (1 + 2) * 3;").interpret().equals("9\n");
        assert new Interpreter("print (1 + (2/3)) * 4;").interpret().equals("4\n");
        assert new Interpreter("print 1 | 2;").interpret().equals("3\n");
        assert new Interpreter("print 2 & 3;").interpret().equals("2\n");
        assert new Interpreter("print 1 || 0;").interpret().equals("1\n");
        assert new Interpreter("print 0 || 2;").interpret().equals("1\n");
        assert new Interpreter("print 0 || 0;").interpret().equals("0\n");
        assert new Interpreter("print 1 && 2;").interpret().equals("1\n");
        assert new Interpreter("print 1 && 0;").interpret().equals("0\n");
        assert new Interpreter("print 0 && 2;").interpret().equals("0\n");
        assert new Interpreter(
            "print 1;\n" +
            "print 2;"
        ).interpret().equals("1\n2\n");
    }
}
