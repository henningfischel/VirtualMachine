/**
 * The Machine Class is a virtual machine that executes a program of integers.
 * All data are integers.
 *
 * @author Henning Fischel
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class Machine {
    private int[] programMemory, stack, globalMem;
    private int pc, sp, fp;
    private int a, b;
    private boolean done, debug = false;

    // the instruction set for the machine
    public final static int
            ADD = 1,        // int add
            SUB = 2,         // int sub
            MUL = 3,        // int mul
            DIV = 4,          // int divide
            LT = 5,         // int less than
            GT = 6,         // int grater than
            LEQ = 7,        // int less than eq
            GEQ = 8,        // int greater than eq
            EQ = 9,         // int equal
            AND = 10,        // boolean and
            OR = 11,        // boolean or
            NOT = 12,       // boolean not
            JMP = 13,        // branch
            JMPT = 14,       // branch if true
            JMPF = 15,       // branch if false
            CONST = 16,      // push constant integer
            LOAD = 17,      // load from local
            GLOAD = 18,     // load from global
            STORE = 19,     // store in local
            GSTORE = 20,    // store in global memory
            PRINT = 21,     // print value on top of the stack
            POP = 22,       // throw away top of the stack
            HALT = 23,      // stop program
            CALL = 24,      // call procedure
            RET = 25,       // return from procedure
            NEG = 26,       // negate an int
            MOD = 27;       // modulo

    public Machine(int[] program) {
        sp = -1;
        pc = 0;
        fp = 0;
        done = false;
        programMemory = program;
        stack = new int[100];
        globalMem = new int[300];
    }

    public Machine(String filepath) {
        sp = -1;
        pc = 0;
        done = false;
        load(filepath);
        stack = new int[100];
        globalMem = new int[300];
    }

    public Machine() {
        sp = -1;
        pc = 0;
        done = false;
        stack = new int[100];
        globalMem = new int[300];
    }

    public Machine(boolean debug) {
        sp = -1;
        pc = 0;
        this.debug = debug;
        done = false;
        stack = new int[100];
        globalMem = new int[300];
    }

    /**
     * loads a program from a filepath
     * @param path
     */
    public void load(String path) {
        try {
            ArrayList<Integer> tmp = new ArrayList<Integer>();
            Scanner in = new Scanner(new File(path));
            while (in.hasNextInt()) {
                tmp.add(in.nextInt());
            }
            programMemory = tmp.stream().mapToInt(Integer::intValue).toArray();
        } catch (FileNotFoundException e) {
            System.err.println(e);
            programMemory = new int[]{HALT};
        }
    }

    /**
     * loads a program from an int array
     * @param program
     */
    public void load(int[] program) {
        programMemory = program.clone();
    }

    /**
     * runs the currently loaded program
     */
    public void run() {
        done = false;
        while (!done) {
            if (debug) System.out.print(Compiler.INT_TO_INSTRUCTION.get(programMemory[pc]));
            switch (programMemory[pc]) {
                case ADD -> {
                    b = pop();
                    a = pop();
                    push(a + b);
                }
                case SUB -> {
                    b = pop();
                    a = pop();
                    push(a - b);
                }
                case MUL -> {
                    b = pop();
                    a = pop();
                    push(a * b);
                }
                case DIV -> {
                    b = pop();
                    a = pop();
                    push(a / b);
                }
                case LT -> {
                    b = pop();
                    a = pop();
                    push(a < b ? 1 : 0);
                }
                case GT -> {
                    b = pop();
                    a = pop();
                    push(a > b ? 1 : 0);
                }
                case LEQ -> {
                    b = pop();
                    a = pop();
                    push(a <= b ? 1 : 0);
                }
                case GEQ -> {
                    b = pop();
                    a = pop();
                    push(a >= b ? 1 : 0);
                }
                case EQ -> {
                    b = pop();
                    a = pop();
                    push(a == b ? 1 : 0);
                }
                case AND -> {
                    b = pop();
                    a = pop();
                    push(a == 1 && b == 1 ? 1 : 0);
                }
                case OR -> {
                    b = pop();
                    a = pop();
                    push(a == 1 || b == 1 ? 1 : 0);
                }
                case NOT -> {
                    a = pop();
                    push(a == 1 ? 0 : a == 0 ? 1 : a);
                }
                case JMP -> {   // pop the address off the stack and change the program counter to it
                    a = pop();
                    pc = a - 1;
                }
                case JMPT -> {
                    b = pop();
                    a = pop();
                    pc = a == 1 ? b - 1 : pc;
                }
                case JMPF -> {
                    b = pop();
                    a = pop();
                    pc = a == 0 ? b - 1 : pc;
                }
                case CONST -> {     // push the number following the command in the program memory to the stack
                    pc += 1;
                    push(programMemory[pc]);
                }
                case LOAD -> {
                    a = pop(); //get the address
                    b = a < 0 ? fp + a - 2 : fp + a + 1;   //avoid the saved data
                    push(stack[b]);
                }
                case GLOAD -> {
                    a = pop(); //get the address
                    push(globalMem[a]);
                }
                case STORE -> {
                    pc += 1;
                    a = pop();  //get the value to store
                    stack[fp - programMemory[pc] + 1] = a; //store
                }
                case GSTORE -> {
                    pc += 1;
                    a = pop();
                    globalMem[programMemory[pc]] = a;
                }
                case PRINT -> System.out.println(pop());
                case POP -> pop();
                case HALT -> done = true;
                case CALL -> {
                    //save the state
                    push(programMemory[pc + 2]);  //save number of args
                    push(pc + 3); //save the address of the next command -1 (since the loop will add one)
                    push(fp);

                    fp = sp; //set the frame pointer to the top of the stack
                    sp += programMemory[pc + 3];   //add space for locals
                    pc = programMemory[pc + 1] - 1;   //branch to the code of the function
                }
                case RET -> {
                    a = pop();      //the return value
                    sp = fp;        //discard locals
                    fp = pop();     //reset frame pointer
                    pc = pop();     //reset the program counter
                    b = pop();  //get number of function args
                    sp -= b;    //discard the function arguments
                    push(a);        //save the return
                }
                case NEG -> {
                    a = pop();
                    push(-a);
                }
                case MOD -> {
                    b = pop();
                    a = pop();
                    push(a % b);
                }
            }

            if (debug) System.out.println(" pc" + pc + " sp" + sp + " fp" + fp + " stack"
                    + Arrays.toString(Arrays.copyOfRange(stack, 0, sp + 1)));
            pc++;
        }
    }

    /**
     * pushes an int to the stack
     * @param n number to be pushed
     */
    private void push(int n) {
        sp++;
        stack[sp] = n;
    }

    /**
     * pops an int from the top of the stack
     * @return the item at the top of the stack
     */
    private int pop() {
        sp--;
        return stack[sp + 1];
    }

    /**
     * Runs a Machine.
     * @param args test (runs a simple test) || load filepath (loads and runs the program at filpath)
     *             || run program (runs a program of ints seperated by commas)
     *             || runSrc filepath (compiles and runs a bytecode file)
     */
    public static void main(String[] args) {
        boolean debug = true;
        Machine m = new Machine(debug);
        switch (args[0]) {
            case "test"-> {
                m.load(new int[]{CONST, 2, CONST, 1, SUB, PRINT, HALT});
                m.run();
            }
            case "runComp"-> {
                m.load(args[1]);
                m.run();
            }
            case "run"-> {
                m.load(Arrays.stream(args[1].split(",")).mapToInt(Integer::parseInt).toArray());
                m.run();
            }
            case "runSrc"-> {
                Lexer lex = new Lexer();
                try {
                    lex.tokenize(args[1]);
                } catch (UnexpectedTokenException e) {
                    System.err.println(e);
                    return;
                }
                String fname = args[1].substring(0, args[1].lastIndexOf('.'));
                Parser.parse(fname + ".vlex");
                Compiler.compile(fname + ".vbyt",
                        fname + ".vcomp");
                m.load(fname + ".vcomp");
                m.run();
            }
            case "runByt"-> {
                String fname = args[1].substring(0, args[1].lastIndexOf('.'));
                Compiler.compile(fname + ".vbyt",
                        fname + ".vcomp");
                m.load(fname + ".vcomp");
                m.run();
            }
        }
    }

}
