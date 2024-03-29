import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.util.*;
import java.util.stream.IntStream;

/**
 *  The Parser class converts a .vlex token file to a bytecode file for compiling to virtual machine code. As an
 *  intermediary step it creates an abstract syntax tree of operation precedence.
 *
 * @author Henning Fischel
 */
public class Parser {
    private ArrayList<Rule> rules;
    private Map<String, Rule> rulesByName;
    private Map<String, Integer> globalVars;
    private Map<String, FunctionContainer> functions;
    private Stack<Map<String, Integer>> localVars;
    private int gVarCount;
    private Stack<Integer> lVarCount;

    public Parser() {
        //use lowercase and beginning with $ to refer to rules and Uppercase to refer to token types
        rules = new ArrayList<Rule>(Arrays.asList(
                // multiline blocks
                new Rule("code_block", new String[][]{new String[]{"FUNC_DEC", "$function"}, new String[]{"WHILE", "$while"},
                        new String[]{"FOR", "$for"}, new String[]{"IF", "$if"}, new String[]{"$lines"}, new String[]{}}, false),
                new Rule("if", new String[][]{new String[]{"OPEN_PAREN", "$or", "CLOSE_PAREN", "OPEN_BRACE",
                        "$code_block", "CLOSE_BRACE", "$code_block"}, new String[]{"OPEN_PAREN", "$or", "CLOSE_PAREN", "OPEN_BRACE",
                        "$code_block", "CLOSE_BRACE"}}, false),
                new Rule("while", new String[][]{new String[]{"OPEN_PAREN", "$or", "CLOSE_PAREN",
                        "OPEN_BRACE", "$code_block", "CLOSE_BRACE", "$code_block"}, new String[]{"OPEN_PAREN", "$or", "CLOSE_PAREN",
                        "OPEN_BRACE", "$code_block", "CLOSE_BRACE"}}, false),
                new Rule("for", new String[][]{new String[]{"OPEN_PAREN", "$assign", "SEMI", "$or", "SEMI",
                        "$assign", "CLOSE_PAREN", "OPEN_BRACE", "$code_block", "CLOSE_BRACE", "$code_block"},
                        new String[]{"OPEN_PAREN", "$assign", "SEMI", "$or", "SEMI", "$assign", "CLOSE_PAREN",
                                "OPEN_BRACE", "$code_block", "CLOSE_BRACE"}}, false),
                new Rule("function", new String[][]{new String[]{"REF", "OPEN_PAREN", "$args_dec",
                        "OPEN_BRACE", "$code_block", "CLOSE_BRACE", "$code_block"},
                        new String[]{"REF", "OPEN_PAREN", "$args_dec", "OPEN_BRACE", "$code_block", "CLOSE_BRACE"}},
                        0, false),
                new Rule("lines", new String[][]{new String[]{"$line", "SEMI"},
                        new String[]{"$line", "SEMI", "$code_block"}, new String[]{"$line", "SEMI"}}, false),

                //line rules
                new Rule("line", new String[][]{new String[]{"$assign"}, new String[]{"$print"},
                        new String[]{"$or"}, new String[]{"$ret"}}, false),
                new Rule("print", new String[][]{new String[]{"PRINT", "OPEN_PAREN", "$or", "CLOSE_PAREN"}},
                        0, false),
                new Rule("assign", new String[][]{new String[]{"$ref", "ASSIGN", "$or"}}, 0, false),
                new Rule("args_dec", new String[][]{new String[]{"$ref", "COMMA", "$args_dec"},
                        new String[]{"$ref", "CLOSE_PAREN"}, new String[]{}}, 0, false),
                new Rule("args", new String[][]{new String[]{"$or", "COMMA", "$args"},
                        new String[]{"$or"}, new String[]{}}, false), //TODO bug??
                new Rule("ret", new String[][]{new String[]{"RET", "$or", "SEMI"}, new String[]{"RET", "$or"}},
                        0, false),

                // boolean ops
                new Rule("or", new String[][]{new String[]{"$and", "OR", "$or"}, new String[]{"$and"}},
                        0, false),
                new Rule("and", new String[][]{new String[]{"$equality", "AND", "$and"},
                        new String[]{"$equality"}}, 0, false),
                new Rule("equality", new String[][]{new String[]{"$comparison", "(", "EQ", "|", "NEQ", ")",
                        "$equality"}, new String[]{"$comparison"}}, 1, false),
                new Rule("comparison", new String[][]{new String[]{"$sum", "(", "GT", "|", "GEQ", "|", "LT", "|",
                        "LEQ", ")", "$comparison"}, new String[]{"$sum"}}, 0, false),

                //arithmetic
                new Rule("sum", new String[][]{new String[]{"$product", "(", "SUB", "|", "ADD", ")", "$sum"},
                        new String[]{"$product"}}, 0, false),
                new Rule("product", new String[][]{new String[]{"$unary", "(", "MUL", "|", "DIV", "|", "MOD", ")",
                        "$product"}, new String[]{"$unary"}}, 0, false),

                //operand types
                new Rule("unary", new String[][]{new String[]{"(", "SUB", "|", "NOT", ")", "$unary"},
                        new String[]{"$item"}}, 0, false),
                new Rule("item", new String[][]{new String[]{"$func_call"}, new String[]{"OPEN_PAREN", "$or",
                        "CLOSE_PAREN"}, {"$const"}, {"$ref"}}, false),
                new Rule("func_call", new String[][]{new String[]{"REF", "OPEN_PAREN", "$args", "CLOSE_PAREN"}},
                        0, false),
                new Rule("const", new String[][]{new String[]{"CONST"}}, true),
                new Rule("ref", new String[][]{new String[]{"REF"}}, true)
        ));
        rulesByName = new HashMap<>();
        for (Rule r : rules) rulesByName.put(r.name, r);
        globalVars = new HashMap<>();
        localVars = new Stack<>();
        gVarCount = 0;
        lVarCount = new Stack<>();
        functions = new HashMap<>();
    }

    /**
     * reads a .vlex file
     * @param inPath the filepath of the .vlex file.
     * @return an ArrayList with two ArrayLists: the token types from the lexer and the original strings
     */
    private static ArrayList<ArrayList<String>> readLexerFile(String inPath) {
        ArrayList<String> toks = new ArrayList<String>();
        ArrayList<String> vals = new ArrayList<String>();

        try {
            Scanner in = new Scanner(new File(inPath));
            while (in.hasNextLine()) {
                String line = in.nextLine();
                ArrayList<String> temp = new ArrayList<>(Arrays.asList(line.split("\\s")));
                for (int i = 0; i < temp.size(); i++) {
                    if (i % 2 == 0) {
                        toks.add(temp.get(i));
                    } else {
                        vals.add(temp.get(i));
                    }
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println(e);
        }
        return new ArrayList<>(Arrays.asList(toks, vals));
    }

    /**
     * The method to call when using Parser. Loads a token file, creates a syntax tree, converts the tree to bytecode,
     * and writes the bytecode to a .vbyt file.
     * @param lexedPath the filepath of the .velx file.
     */
    public static void parse(String lexedPath) {
        assert lexedPath.substring(lexedPath.lastIndexOf('.')).equals(".vlex");
        Parser p = new Parser();
        ArrayList<ArrayList<String>> toksAndVals = readLexerFile(lexedPath);
        ExpressionNode ast = p.rules.get(0).buildAstFromThisRule(toksAndVals.get(0), toksAndVals.get(1));
        String program = p.compile(ast);
        write(program, lexedPath.substring(0, lexedPath.lastIndexOf('.')) + ".vbyt");
    }

    /**
     * Convert a Syntax tree into bytecode. Functions are added to the end of the file.
     * @param astHead the head of the tree.
     * @return the bytecode program
     */
    private String compile(ExpressionNode astHead) {
        String s = compileHelper(astHead, -1, false);  // first pass; write everything except the functions
        s = s + "HALT\n";   // add the stop code to the end
        int pc = s.split("\\s").length - 1; // the number of operations and args

        // iterate over the functions
        for (String funcName : functions.keySet()) {
            FunctionContainer func = functions.get(funcName);
            int funcAddr = pc + 1;  // the address to branch to the function
            localVars.push(new HashMap<>());
            lVarCount.push(0);
            s = s + "\n#func " + funcName + "\n";   // add a comment

            // parse the function's args and code
            String temp = compileHelper(func.args, pc, true);
            s = s + temp;
            pc += temp.split("\\s").length;
            temp = compileHelper(func.code, pc, true);
            s = s + temp;
            pc += temp.split("\\s").length;

            // add the correct address to the function calls to this function
            s = s.replaceAll("\\$" + func.addr, String.valueOf(funcAddr));
            localVars.pop();
            lVarCount.pop();
        }
        System.out.println(s);
        return s;
    }

    /**
     * Recursive function that traverses the syntax tree and builds the bytecode string
     * @param eNode the current node
     * @param pc the current program counter
     * @param inFunction whether the code is currently in a function (i.e. whether to use local or global vars)
     * @return the bytecode program
     */
    private String compileHelper(ExpressionNode eNode, int pc, boolean inFunction) {
        StringBuilder strB = new StringBuilder();
        // switch on the type of rule
        switch (eNode.rule.name) {
            // for collections of code just evaluate the children
            case "code_block", "lines", "line", "item" -> {
                for (ExpressionNode e : eNode.children) {
                    String tmp = compileHelper(e, pc, inFunction);
                    strB.append(tmp);
                    pc += tmp.split("\\s").length;
                }
            }
            case "if" -> {
                // write condition
                String tmp = compileHelper(eNode.children.get(0), pc, inFunction);
                strB.append(tmp);
                pc += tmp.split("\\s").length;
                // get code block
                tmp = compileHelper(eNode.children.get(1), pc, inFunction);
                pc += tmp.split("\\s").length;
                // add jump if condition is false
                strB.append("CONST " + (pc + 3) + " JMPF\n");
                // write code block
                strB.append(tmp);
                //process next code block
                if (eNode.children.size() > 2) {
                    strB.append(compileHelper(eNode.children.get(2), pc + 3, inFunction));
                }
            }
            case "while" -> {
                //store pc
                int conditionPC = pc + 1;
                // write condition
                String tmp = compileHelper(eNode.children.get(0), pc, inFunction);
                strB.append(tmp);
                pc += tmp.split("\\s").length;
                // get code block
                tmp = compileHelper(eNode.children.get(1), pc + 3, inFunction);
                pc += tmp.split("\\s").length;
                // add jump if condition is false
                strB.append("CONST " + (pc + 7) + " JMPF\n");
                //write code block
                strB.append(tmp);
                // add jump back to condition
                strB.append("CONST " + conditionPC + " JMP\n");
                //process next code block
                if (eNode.children.size() > 2) {
                    strB.append(compileHelper(eNode.children.get(2), pc + 3, inFunction));
                }
            }
            case "for" -> {
                // write init
                String tmp = compileHelper(eNode.children.get(0), pc, inFunction);
                strB.append(tmp);
                pc += tmp.split("\\s").length;
                // store pc
                int conditionPC = pc + 1;
                // write condition
                tmp = compileHelper(eNode.children.get(1), pc, inFunction);
                strB.append(tmp);
                pc += tmp.split("\\s").length;
                // get code block and increment
                tmp = compileHelper(eNode.children.get(3), pc + 3, inFunction);
                pc += tmp.split("\\s").length;
                String increment = compileHelper(eNode.children.get(2), pc, inFunction);
                pc += increment.split("\\s").length;
                // add jump if condition is false
                strB.append("CONST " + (pc + 7) + " JMPF\n");
                // write code block and increment
                strB.append(tmp);
                strB.append(increment);
                // add jump back to condition
                strB.append("CONST " + conditionPC + " JMP\n");

                //process next code block
                if (eNode.children.size() > 4) {
                    strB.append(compileHelper(eNode.children.get(4), pc + 3, inFunction));
                }
            }
            case "function" -> {
                String fName = eNode.value.split(" ")[0];
                lVarCount.push(0);
                localVars.push(new HashMap<>());
                // eval args for the number of args
                compileHelper(eNode.children.get(0), pc, true);
                int nArgs = lVarCount.pop();
                // add the function to the function dictionary to be converted later
                functions.put(fName, new FunctionContainer(fName, eNode.children.get(0),
                        eNode.children.get(1), nArgs, -1, functions.size()));

                // eval code block for number of locals
                lVarCount.push(0);
                compileHelper(eNode.children.get(1), pc, true);
                int nLocals = lVarCount.pop();
                localVars.pop();
                functions.get(fName).nLocals = nLocals;

                //process the next code block
                if (eNode.children.size() > 2) {
                    strB.append(compileHelper(eNode.children.get(2), pc, inFunction));
                }
            }
            case "print" -> {
                strB.append(compileHelper(eNode.children.get(0), pc, inFunction));
                strB.append("PRINT\n");
            }
            case "assign" -> {
                String varName = eNode.children.get(0).value;
                if (inFunction) {
                    // store the variable locally
                    if (!localVars.peek().containsKey(varName)) {
                        localVars.peek().put(varName, lVarCount.peek());
                        lVarCount.push(lVarCount.pop() + 1);
                    }
                    strB.append(compileHelper(eNode.children.get(1), pc, inFunction));
                    strB.append("STORE ").append(localVars.peek().get(varName)).append("\n");
                } else {
                    // store the variable globally
                    if (!globalVars.containsKey(varName)) {
                        globalVars.put(varName, gVarCount);
                        gVarCount++;
                    }
                    strB.append(compileHelper(eNode.children.get(1), pc, inFunction));
                    strB.append("GSTORE ").append(globalVars.get(varName)).append("\n");
                }
            }
            case "args_dec" -> {
                localVars.peek().put(eNode.children.get(0).value, -lVarCount.peek() - 1);
                lVarCount.push(lVarCount.pop() + 1);
                if (eNode.children.size() > 1) {
                    strB.append(compileHelper(eNode.children.get(1), pc, inFunction));
                }
            }
            case "args" -> {
                // needs to be done in reverse order
                if (eNode.children.size() > 1) {
                    strB.append(compileHelper(eNode.children.get(1), pc, inFunction));
                }
                strB.append(compileHelper(eNode.children.get(0), pc, inFunction));
            }
            case "ret" -> {
                strB.append(eNode.children.isEmpty() ? "CONST -1" : compileHelper(eNode.children.get(0), pc, inFunction));
                strB.append("RET\n");
            }
            case "sum", "product", "and", "or", "comparison" -> {
                String tmp = compileHelper(eNode.children.get(0), pc, inFunction);
                strB.append(tmp);
                pc += tmp.split("\\s").length;
                tmp = compileHelper(eNode.children.get(1), pc, inFunction);
                strB.append(tmp);
                strB.append(eNode.op).append("\n");
            }
            case "unary" -> {
                if (eNode.op.equals("SUB")) {
                    strB.append(compileHelper(eNode.children.get(0), pc, inFunction));
                    strB.append("NEG\n");
                } else {    //op = "NOT"
                    strB.append(compileHelper(eNode.children.get(0), pc, inFunction));
                    strB.append(" NOT\n");
                }
            }
            case "const" -> {
                strB.append("CONST ").append(eNode.value).append("\n");
            }
            case "ref" -> {
                if (inFunction && localVars.peek().containsKey(eNode.value)) {
                    strB.append("CONST ").append(localVars.peek().get(eNode.value)).append(" LOAD\n");
                } else if (globalVars.containsKey(eNode.value)) {
                    strB.append("CONST ").append(globalVars.get(eNode.value)).append(" GLOAD\n");
                } else {
                    System.err.println("Can't find var " + eNode.value);
                }
            }
            case "func_call" -> {
                // Str =
                //      Evaluate Args
                //      Call [branch addr] [num args] [num locals]
                String fName = eNode.value.split(" ")[0];
                FunctionContainer func = functions.getOrDefault(fName, null);
                if (func != null) {
                    strB.append(compileHelper(eNode.children.get(0), pc, true));     // evaluate args
                    strB.append("CALL $").append(func.addr).append(" ").append(func.nArgs).append(" ")  // call the func
                            .append(func.nLocals).append("\n");
                } else {
                    System.err.println("func " + fName + " not found");
                }
            }
        }
        return strB.toString();
    }

    private static void write(String program, String outfile) {
        try {
            File outFile = new File(outfile);
            outFile.delete();
            outFile.createNewFile();
        } catch (Exception e) {
            System.err.println(e);
        }
        try {
            FileWriter writer = new FileWriter(outfile);
            writer.write(program);
            writer.close();
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    public static void main(String[] args) {
        Lexer lex = new Lexer();
        try {
            lex.tokenize(args[0]);
        } catch (UnexpectedTokenException e) {
            System.err.println(e);
            return;
        }
        Parser.parse(args[0].substring(0, args[0].lastIndexOf('.')) + ".vlex");
        Compiler.compile(args[0].substring(0, args[0].lastIndexOf('.')) + ".vbyt",
                args[0].substring(0, args[0].lastIndexOf('.')) + ".vcomp");
        Machine m = new Machine(args[0].substring(0, args[0].lastIndexOf('.')) + ".vcomp");
        m.run();
    }

    /**
     * The node class for a Syntax tree.
     */
    private class ExpressionNode {
        private Rule rule;  // the rule associated with this Node
        private String value;   // the source code at this node
        private ArrayList<ExpressionNode> children;
        private String op;  // the string value of the operation perfomed at this node (e.g. "+").

        public ExpressionNode(Rule rule, String value, ArrayList<ExpressionNode> children) {
            this.rule = rule;
            this.value = value;
            this.children = children;
            this.op = null;
        }

        public String toString() {
            return toStringHelper(1);
        }

        private String toStringHelper(int depth) {
            StringBuilder s = new StringBuilder(rule.name + " " + value);
            if (children != null) {
                for (ExpressionNode child : children) {
                    s.append("\n");
                    for (int i = 0; i < depth; i++) {
                        s.append("\t");
                    }
                    s.append(child.toStringHelper(depth + 1));
                }
            }
            return s.toString();
        }

    }

    /**
     * A particular grammar rule.
     */
    private class Rule {
        private final String name;  // what this rule is referred to by in other Rules' patterns.
        private final String[][] patterns;  // the possible patterns of tokens that are associated with this rule.
        boolean terminal;   // does this rule not contain any other rules in its patterns.
        int opIdx;  // the index of the token that corresponds to the operation this rule performs in the patterns.

        public Rule(String name, String[][] patterns, int opIdx, boolean terminal) {
            this.name = name;
            this.patterns = patterns.clone();
            this.terminal = terminal;
            this.opIdx = opIdx;
        }

        public Rule(String name, String[][] patterns, boolean terminal) {
            this.name = name;
            this.patterns = patterns.clone();
            this.terminal = terminal;
            this.opIdx = -1;
        }

        public String[][] getPatterns() {
            return patterns;
        }

        /**
         *  Creates a syntax tree starting from this rule. Recursively calls this method in other Rules. Should be
         *  called by the rule with the lowest precedence.
         * @param toks the tokens to parse
         * @param vals Strign values of the tokens in toks
         * @return an ExpressionNode that is the head of the tree.
         */
        public ExpressionNode buildAstFromThisRule(List<String> toks, List<String> vals) {
            // if this is a terminal then just check to see if the toks match the pattern
            if (this.terminal) {
                if (toks.get(0).equals(getPatterns()[0][0]) && toks.size() == 1)
                    return new ExpressionNode(this, vals.get(0), null);
                else return null;
            }
            // check if toks matches any pattern in patterns
            for (String[] pattern : patterns) {
                // ran out of toks or vals to parse; time to early exit.
                if (pattern.length == 0 || toks.size() == 0) {
                    if (toks.size() == 0 && pattern.length == 0) {
                        return new ExpressionNode(this, "", null);
                    } else {
                        return null;
                    }
                }
                // if the pattern just has one operation and is not a terminal then it is just a pointer to another rule
                if (pattern.length == 1) {
                    ExpressionNode e = rulesByName.get(pattern[0].substring(1)).buildAstFromThisRule(toks, vals);
                    if (e == null) continue;
                    return e;
                } else {
                    boolean inSubExp = false;
                    int patternPointer = 0; //pointer for pattern. Set to -1 if there is an error in the match
                    int patternPointerUpdate = -1; // the index to move patternPointer to after a match
                    int fromIdx = -1;   // the start of a subexpression
                    String op = null;
                    ArrayList<ArrayList<String>> nextMatches;   // the list of tokens that can be the next match
                    try {
                        nextMatches = findNextMatches(new ArrayList<>(Arrays.asList(pattern)), 0);
                        if (nextMatches != null)
                            patternPointerUpdate = Integer.parseInt(nextMatches.remove(0).get(0));
                    } catch (UnexpectedTokenException e) {
                        System.err.println(e);
                        return null;
                    }

                    ArrayList<ExpressionNode> children = new ArrayList<>();

                    // iterate over the token list
                    for (int tokenPointer = 0; tokenPointer < toks.size(); tokenPointer++) {
                        // if out of pattern then there it does not match. Early breakout.
                        if (patternPointer >= pattern.length) {
                            patternPointer = -1;
                            break;
                        }
                        //currently in a sub-expression
                        if (pattern[patternPointer].charAt(0) == '$') {
                            //if just started the recursive rule part, start counting
                            if (!inSubExp) {
                                inSubExp = true;
                                fromIdx = tokenPointer;
                                try {
                                    nextMatches = findNextMatches(new ArrayList<>(Arrays.asList(pattern)),
                                            patternPointer);
                                } catch (UnexpectedTokenException e) {
                                    System.err.println(e);
                                    return null;
                                }
                                //if this subExp is the last pattern then add the rest of the toks to the last subarr
                                if (nextMatches == null) {
                                    ExpressionNode e = rulesByName.get(pattern[patternPointer].substring(1))
                                            .buildAstFromThisRule(toks.subList(fromIdx, toks.size())
                                                    , vals.subList(fromIdx, toks.size()));
                                    if (e == null) break;
                                    patternPointer = pattern.length;
                                    children.add(e);
                                    break;
                                }
                                patternPointerUpdate = Integer.parseInt(nextMatches.remove(0).get(0));
                            }

                            //if the next pattern matches then exit sub expression
                            int matchidx = -1;
                            for (ArrayList<String> match : nextMatches) {
                                if (match.get(0).equals(toks.get(tokenPointer))) {
                                    matchidx = nextMatches.indexOf(match);
                                    break;
                                }
                            }
                            // if there is a match then check if the subexpression matches
                            if (matchidx > -1) {
                                ExpressionNode e = rulesByName.get(pattern[patternPointer].substring(1))
                                        .buildAstFromThisRule(toks.subList(fromIdx, tokenPointer)
                                                , vals.subList(fromIdx, tokenPointer));
                                if (e == null) continue;    // if it doesn't match then keep looking (staying in the sub
                                    // expression)
                                children.add(e);
                                if (patternPointer == this.opIdx) op = toks.get(tokenPointer);
                                patternPointer = patternPointerUpdate;
                                inSubExp = false;
                            }
                        // if not in a sub-expression
                        } else {
                            // find the next possible tokens that would match
                            try {
                                nextMatches = findNextMatches(new ArrayList<>(Arrays.asList(pattern)),
                                        patternPointer);
                                patternPointerUpdate = Integer.parseInt(nextMatches.remove(0).get(0));
                            } catch (UnexpectedTokenException e) {
                                System.err.println(e);
                                return null;
                            }

                            // check if the tokens match
                            int matchidx = -1;
                            for (ArrayList<String> match : nextMatches) {
                                if (match.get(0).equals(toks.get(tokenPointer))) {
                                    matchidx = nextMatches.indexOf(match);
                                    break;
                                }
                            }
                            if (matchidx > -1) {
                                if (patternPointer == this.opIdx) op = toks.get(tokenPointer);
                                patternPointer = patternPointerUpdate;
                            } else {
                                break;
                            }
                        }
                    }

                    if (patternPointer >= pattern.length) { // this means there was a match
                        String temp = "";
                        for (String s : vals) temp = temp.concat(s).concat(" ");
                        ExpressionNode e = new ExpressionNode(this, temp, children);
                        if (op != null) e.op = op;
                        return e;
                    }
                }
            }
            String temp = "";
            for (String s : toks) temp = temp.concat(s + " ");
            return null;
        }

        /**
         * Returns an Arraylist of the possible patterns that will break buildAstFromThisRule out of a sub-rule.
         *
         * @param pattern the pattern to find the next possible matches in. This may not contain nested parentheses.
         * @param start   the index to start looking from
         * @return A list of the possible patterns that will break buildAstFromThisRule out of a sub-rule. The first
         * dimension are multiple possible patterns separated by or statements. The second dimension is used for
         * patterns that contain multiple tokens. Will return null if the only statements are subexpressions. The first
         * index is the length of the matched area.
         * @throws UnexpectedTokenException throws an excpetion when there are mismatched parentheses
         */
        private ArrayList<ArrayList<String>> findNextMatches(ArrayList<String> pattern, int start)
                throws UnexpectedTokenException {
            int nextPointer = start;
            String nextTok = pattern.get(nextPointer);
            while (nextTok.charAt(0) == '$') {
                nextPointer++;
                if (nextPointer == pattern.size()) return null;
                nextTok = pattern.get(nextPointer);
            }
            if (nextTok.equals("(")) {
                //find the idx of the closing paren
                int closeParenIdx = pattern.indexOf(")");
                if (closeParenIdx == -1) {
                    throw new UnexpectedTokenException("No Closing Paren");
                }
                List<String> inParens = pattern.subList(nextPointer + 1, closeParenIdx);
                ArrayList<ArrayList<String>> matches = new ArrayList<>();
                matches.add(new ArrayList<>(List.of(Integer.toString(closeParenIdx + 1))));

                //split inParens by occurance of "|" and add to matches
                int[] indices = IntStream.range(0, inParens.size())
                        .filter(i -> inParens.get(i).equals("|"))
                        .toArray();
                int lastIdx = -1;
                for (int orIdx : indices) {
                    matches.add(new ArrayList<>(inParens.subList(lastIdx + 1, orIdx)));
                    lastIdx = orIdx;
                }
                matches.add(new ArrayList<>(inParens.subList(lastIdx + 1, inParens.size())));

                return matches;
            } else {
                return new ArrayList<>(List.of(new ArrayList<>(List.of(Integer.toString(nextPointer + 1))),
                        new ArrayList<>(List.of(nextTok))));
            }
        }

    }

    /**
     * A container class to group the variables associated with a particular function.
     */
    private class FunctionContainer {
        String name;
        ExpressionNode args;
        ExpressionNode code;
        int nArgs, nLocals, addr;

        public FunctionContainer(String name, ExpressionNode args, ExpressionNode code, int nArgs, int nLocals, int addr) {
            this.name = name;
            this.args = args;
            this.code = code;
            this.nArgs = nArgs;
            this.nLocals = nLocals;
            this.addr = addr;
        }
    }
}
