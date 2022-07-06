/**
 * The Compiler Class converts a file of english text instructions for Machine and converts them into a file of integers
 * that a Machine object can execute.
 *
 * @author  Henning Fischel
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

import static java.util.Map.entry;

public class Compiler {

    public static final Map<String,Integer> INSTRUCTIONS = Map.ofEntries(
         entry("ADD", 1),        // int add
         entry("SUB", 2),         // int sub
         entry("MUL", 3),        // int mul
         entry("DIV", 4),          // int divide
         entry("LT", 5),         // int less than
         entry("GT", 6),         // int grater than
         entry("LEQ", 7),        // int less than eq
         entry("GEQ", 8),        // int greater than eq
         entry("EQ", 9),         // int equal
         entry("AND", 10),        // boolean and
         entry("OR", 11),        // boolean or
         entry("NOT", 12),       // boolean not
         entry("JMP", 13),        // branch
         entry("JMPT", 14),       // branch if true
         entry("JMPF", 15),       // branch if false
         entry("CONST", 16),      // push constant integer
         entry("LOAD", 17),      // load from local
         entry("GLOAD", 18),     // load from global
         entry("STORE", 19),     // store in local
         entry("GSTORE", 20),    // store in global memory
         entry("PRINT", 21),     // print value on top of the stack
         entry("POP", 22),       // throw away top of the stack
         entry("HALT", 23),      // stop program
         entry("CALL", 24),      // call procedure
         entry("RET", 25)       // return from procedure
    );

    public static final Map<Integer,String> INT_TO_INSTRUCTION = //an inverse map of INSTRUCTIONS
            INSTRUCTIONS.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));


    public Compiler(){
    }

    private static ArrayList<String> readFromFile(String infile) {
        ArrayList<String> tokens = new ArrayList<>();
        try{
            Scanner in = new Scanner(new File(infile));
            while(in.hasNextLine()){
                String line = in.nextLine();
                if(!line.contains("#"))
                    tokens.addAll(Arrays.asList(line.split("\\s")));
            }
        } catch (FileNotFoundException e) {
            System.err.println(e);
        }
        return tokens;
    }

    private static int[] tokensToInts(ArrayList<String> tokens) {
        int[] ints = new int[tokens.size()];
        int skippedTokens = 0;
        int prgrmPointer = 0;
        for(String token : tokens) {
            if(token.isEmpty()){
                skippedTokens++;
                continue;
            }
            if(INSTRUCTIONS.containsKey(token)){
                ints[prgrmPointer]= INSTRUCTIONS.get(token);
                prgrmPointer++;
            } else {
                try {
                    int tmp = Integer.parseInt(token);
                    ints[prgrmPointer] = tmp;
                    prgrmPointer++;
                } catch (NumberFormatException e){
                    System.err.println(e);
                    skippedTokens++;
                }
            }
        }
        return Arrays.copyOfRange(ints,0,ints.length-skippedTokens);
    }

    private static void writeProgram(int[] program, String outfile) {
        try {
            File outFile = new File(outfile);
            outFile.delete();
            outFile.createNewFile();
        } catch (Exception e){
            System.err.println(e);
        }
        try {
            FileWriter writer = new FileWriter(outfile);
            for(int instruction : program){
                writer.write(Integer.toString(instruction));
                writer.write(' ');
            }

            writer.close();
        } catch (Exception e){
            System.err.println(e);
        }
    }

    public static void compile(String infile,String outfile){
        ArrayList<String> tokens = readFromFile(infile);
        int[] program = tokensToInts(tokens);
        writeProgram(program,outfile);
    }

    public static void main(String[] args) {
        if(args.length==2){
            compile(args[0],args[1]);
        } else {
            System.err.println("Not enough arguments. Please use arguments: input_file output_file");
        }
    }
}
