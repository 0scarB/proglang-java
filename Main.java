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
    LPAREN,
    RPAREN,
    SEMI,
}
class Token {
    public final TokenType type;

    public Token(TokenType type) {
        this.type = type;
    }

    public int getIntVal() {
        return 0;
    }

    public String toString() {
        return type.name();
    }
}
class IntToken extends Token {
    private int val;

    public IntToken(TokenType type, int val) {
        super(type);
        this.val = val;
    }

    @Override
    public int getIntVal() {
        return this.val;
    }

    @Override
    public String toString() {
        return type.name() + "(" + val + ")";
    }
}
class Tokenizer {
    private String src;
    private int    pos = 0;

    public Tokenizer(String src) {
        this.src = src;
    }

    private char currChar() {
        return pos < src.length() ? src.charAt(pos) : 0;
    }
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
                int x = (int)(c - '0');
                for (;;) {
                    c = nextChar();
                    if (c < '0' || '9' < c) break;
                    x = 10*x + (int)(c - '0');
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
            default:
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
}
record BinExpr(BinOp op, Expr lhs, Expr rhs) implements Expr {
    public String toString() {
        return "(" + op.name() + " " + lhs + " " + rhs + ")";
    }
}
class Parser {
    private Tokenizer tokenizer;

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
                    "Expression not closed with matching right parenthsis!");
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
            case ADD -> BinOp.ADD;
            case SUB -> BinOp.SUB;
            case MUL -> BinOp.MUL;
            case DIV -> BinOp.DIV;
            default -> {
                throw new ParseError(
                    "Expected binary-operator after expression left-hand-side!");
            }
        };
        Expr rhs = parseExpr();
        return new BinExpr(op, lhs, rhs);
    }

    public Expr parse() throws ParseError {
        Expr expr = parseExpr();    
        Token tok = tokenizer.next();
        if (tok.type != TokenType.SEMI && 
            tok.type != TokenType.EOF
        ) {
            throw new ParseError(
                "Expected semicolon or end of file after expression!");
        }
        return expr;
    }
}

class Interpreter {
    private Parser parser;

    public Interpreter(String src) {
        parser = new Parser(src);
    }

    private int evalExpr(Expr expr) throws InterpreterError {
        if (expr instanceof IntVal intVal) {
            return intVal.val();
        }

        if (expr instanceof BinExpr binExpr) {
            int a = evalExpr(binExpr.lhs());
            int b = evalExpr(binExpr.rhs());
            switch (binExpr.op()) {
                case ADD: return a + b;
                case SUB: return a - b;
                case MUL: return a * b;
                case DIV:
                    if (b == 0)
                        throw new InterpreterError("Division by zero!");
                    return a / b;
            }
        }

        throw new InterpreterError("Unexpected expression type!");
    }

    public int interpret() throws ParseError, InterpreterError {
        Expr expr = parser.parse();
        return evalExpr(expr);
    }
}

public class Main {
    public static void main(String[] args) throws ParseError, InterpreterError {
        //Tokenizer tokenizer = new Tokenizer("1 + 2");
        //for (;;) {
        //    Token tok = tokenizer.next();
        //    System.out.println(tok);
        //    if (tok.type == TokenType.EOF) break;
        //}

        Parser parser = new Parser("1 + 2");
        System.out.println(parser.parse());

        parser = new Parser("1 + 2*3");
        System.out.println(parser.parse());

        parser = new Parser("(1 + 2) * 3");
        System.out.println(parser.parse());

        parser = new Parser("(1 + (2/3)) * 4");
        System.out.println(parser.parse());

        assert new Interpreter("1 + 2").interpret() == 3;
        assert new Interpreter("1 + (2*3)").interpret() == 7;
        assert new Interpreter("(1 + 2) * 3").interpret() == 9;
        assert new Interpreter("(1 + (2/3)) * 4").interpret() == 4;
    }
}
