/**
 * The Machine Class is a virtual machine that executes a program
 *
 * @author  Henning Fischel
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class Machine {
    private int[] programMemory, stack, globalMem;
    private int pc,sp, fp;
    private  int a,b;
    private boolean done, debug = false;

    // the instruction set for the machine
    public final static int
            ADD = 1,        // int add
            SUB= 2,         // int sub
            MUL = 3,        // int mul
            DIV=4,          // int divide
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
            RET = 25;       // return from procedure

    public Machine(int[] program){
        sp = -1;
        pc = 0;
        fp = 0;
        done = false;
        programMemory = program;
        stack = new int[100];
        globalMem = new int[300];
    }

    public Machine(String filepath){
        sp = -1;
        pc = 0;
        done = false;
        load(filepath);
        stack = new int[100];
        globalMem = new int[300];
    }

    public Machine(){
        sp = -1;
        pc = 0;
        done = false;
        stack = new int[100];
        globalMem = new int[300];
    }
    public Machine(boolean debug){
        sp = -1;
        pc = 0;
        this.debug = debug;
        done = false;
        stack = new int[100];
        globalMem = new int[300];
    }

    public void load(String path){
        try{
            ArrayList<Integer> tmp = new ArrayList<Integer>();
            Scanner in = new Scanner(new File(path));
            while(in.hasNextInt()){
                tmp.add(in.nextInt());
            }
            programMemory=tmp.stream().mapToInt(Integer::intValue).toArray();
        } catch (FileNotFoundException e) {
            System.err.println(e);
            programMemory = new int[] {HALT};
        }
    }

    public void load(int[] program){
        programMemory = program.clone();
    }

    public void run(){
        done = false;
        while(!done){
            if(debug) System.out.print(Compiler.INT_TO_INSTRUCTION.get(programMemory[pc]));
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
                    push(a<b? 1:0);
                }
                case GT -> {
                    b = pop();
                    a = pop();
                    push(a>b? 1:0);
                }
                case LEQ -> {
                    b = pop();
                    a = pop();
                    push(a<=b? 1:0);
                }
                case GEQ -> {
                    b = pop();
                    a = pop();
                    push(a>=b? 1:0);
                }
                case EQ -> {
                    b = pop();
                    a = pop();
                    push(a==b? 1:0);
                }
                case AND -> {
                    b = pop();
                    a = pop();
                    push(a==1&&b==1? 1:0);
                }
                case OR -> {
                    b = pop();
                    a = pop();
                    push(a==1||b==1? 1:0);
                }
                case NOT -> {
                    a = pop();
                    push(a==1? 0: a==0? 1: a);
                }
                case JMP -> {   // pop the address off the stack and change the program counter to it
                    a = pop();
                    pc = a-1;
                }
                case JMPT -> {
                    b = pop();
                    a = pop();
                    pc = a==1? b-1:pc;
                }
                case JMPF -> {
                    b = pop();
                    a = pop();
                    pc = a==0? b-1:pc;
                }
                case CONST -> {     // push the number following the command in the program memory to the stack
                    pc += 1;
                    push(programMemory[pc]);
                }
                case LOAD -> {
                    a = pop(); //get the address
                    push(stack[fp+a-2]);
                }
                case GLOAD -> {
                    a = pop(); //get the address
                    push(globalMem[a]);
                }
                case STORE -> {
                    pc+=1;
                    a = pop();  //get the value to store
                    stack[fp-programMemory[pc]] = a; //store
                }
                case  GSTORE -> {
                    pc+=1;
                    a = pop();
                    globalMem[programMemory[pc]] = a;
                }
                case PRINT -> System.out.println(pop());
                case POP -> pop();
                case HALT -> done = true;
                case CALL -> {
                    //save the state
                    push(programMemory[pc+2]);  //save number of args
                    push(pc+2); //save the address of the next command -1 (since the loop will add one)
                    push(fp);

                    fp = sp; //set the frame pointer to the top of the stack
                    pc = programMemory[pc+1];   //branch to the code of the function
                }
                case RET -> {
                    a = pop();      //the return value
                    sp = fp;        //discard locals
                    fp = pop();     //reset frame pointer
                    pc = pop();     //reset the program counter
                    sp -= pop();    //discard the function arguments
                    push(a);        //save the return
                }
            }

            if(debug) System.out.println(" pc"+pc+" sp"+sp+" fp"+fp+" stack"+Arrays.toString(stack));
            pc++;
        }
    }

    private void push(int n){
        sp++;
        stack[sp] = n;
    }

    private int pop(){
        sp--;
        return stack[sp+1];
    }

    public static void main(String[] args) {
        Machine m = new Machine(true);
        switch (args[0]) {
            case "test":
                m.load(new int[] {CONST,2,CONST,1,SUB,PRINT,HALT});
                m.run();
                break;
            case "load":
                m.load(args[1]);
                m.run();
                break;
            case "run":
                m.load(Arrays.stream(args[1].split(",")).mapToInt(Integer::parseInt).toArray());
                m.run();
                break;
            case "runSrc":
                String fname = args[1].substring(0,args[1].lastIndexOf('.'))+".vcomp";
                Compiler.compile(args[1],fname);
                m.load(fname);
                m.run();
        }
    }

}
