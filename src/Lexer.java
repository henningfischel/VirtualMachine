import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.util.*;

public class Lexer {

    public static Map<String,String > OPERATION_STRINGS = Map.ofEntries(
            Map.entry("var","VAR_DEC"),
            Map.entry("func","FUNC_DEC"),
            Map.entry("\\+","ADD"),
            Map.entry("-", "SUB"),
            Map.entry("\\*","MUL"),
            Map.entry("\\/","DIV"),
            Map.entry(">","GT"),
            Map.entry(">=","GEQ"),
            Map.entry("<","LT"),
            Map.entry("<=","LEQ"),
            Map.entry("==","EQ"),
            Map.entry("!","NOT"),
            Map.entry("!=","NEQ"),
            Map.entry("\\|\\|","OR"),
            Map.entry("\\&\\&","AND"),
            Map.entry("\\(","OPEN_PAREN"),
            Map.entry("\\)","CLOSE_PAREN"),
            Map.entry("\\{","OPEN_BRACE"),
            Map.entry("\\}","CLOSE_BRACE"),
            Map.entry("if","IF"),
            Map.entry("else","ELSE"),
            Map.entry("while","WHILE"),
            Map.entry("for","FOR"),
            Map.entry("[0-9]","CONST"),
            Map.entry("^[a-zA-Z][a-zA-Z0-9]*$","REF"),
            Map.entry(";","SEMI"),
            Map.entry("print","PRINT"),
            Map.entry("=","ASSIGN"),
            Map.entry("return","RET"),
            Map.entry(",","COMMA")
    );
    ArrayList<String> rawTokens;
    ArrayList<ArrayList<String>> tokensWithTypes;
    String splitRegex;
    public Lexer(){
        rawTokens = new ArrayList<>();
        tokensWithTypes = new ArrayList<>();
        ArrayList<String> extraSplitTokens = new ArrayList<>(Arrays.asList("\\(", "\\)", "\\[", "\\]", "\\{", "\\}",
                "<", ">", "<=", ">=", "\\+", "-","\\/","\\*","=","==","!=",";","\\|\\|","\\&\\&"));
        splitRegex = "\\t|\\n| ";
        for(String s : extraSplitTokens) splitRegex = splitRegex.concat("|((?="+s+")|(?<="+s+"))");
    }

    private void readFromFile(String infile) {
        try{
            Scanner in = new Scanner(new File(infile));
            while(in.hasNextLine()){
                String line = in.nextLine();
                if(line.contains("#")) continue;
                //split removing whitespace and around parens
                ArrayList<String> lineToks = new ArrayList<>(Arrays.asList(line.split(splitRegex)));
                lineToks.removeIf(String::isEmpty);
                rawTokens.addAll(lineToks);
            }
        } catch (FileNotFoundException e) {
            System.err.println(e);
        }
    }

    public void tokenize(String inPath) throws UnexpectedTokenException {
        readFromFile(inPath);
        Set<String> keys =OPERATION_STRINGS.keySet();
        String refRegex = "^[a-zA-Z][a-zA-Z0-9]*$";
        for (String s : rawTokens) {
            boolean matched = false;
            for (String regex : keys){
                //make sure if/while/... statements don't match with variable and function references
                if(OPERATION_STRINGS.get(regex).equals("REF")) continue;
                else if(s.matches(regex)) {
                    tokensWithTypes.add(new ArrayList<>(Arrays.asList(s, OPERATION_STRINGS.get(regex))));
                    matched = true;
                    break;
                }
            }
            if(!matched) {
                if(s.matches(refRegex)) {
                    tokensWithTypes.add(new ArrayList<>(Arrays.asList(s, OPERATION_STRINGS.get(refRegex))));
                } else {
                    throw new UnexpectedTokenException(s);
                }
            }
        }
        writeLexed(inPath.substring(0,inPath.lastIndexOf('.'))+".vlex");
    }

    private void writeLexed(String outfile) {
        try {
            File outFile = new File(outfile);
            outFile.delete();
            outFile.createNewFile();
        } catch (Exception e){
            System.err.println(e);
        }
        try {
            FileWriter writer = new FileWriter(outfile);
            for(ArrayList<String> tokenAndType: tokensWithTypes){
                writer.write(tokenAndType.get(1)+" "+tokenAndType.get(0));
                writer.write('\n');
            }

            writer.close();
        } catch (Exception e){
            System.err.println(e);
        }
    }

    public static void main(String[] args){
        Lexer l = new Lexer();
        try {
            l.tokenize(args[0]);
        } catch (UnexpectedTokenException e){
            System.err.println(e);
        }
    }
}
