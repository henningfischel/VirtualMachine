import java.util.ArrayList;
import java.util.Arrays;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class Machine {
    private int[] programMemory, stack, globalMem, localMem;
    private int pc,sp, fp;
    private  int a,b;
    private boolean done;

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
        done = false;
        programMemory = program;
        stack = new int[2^8];
        globalMem = new int[2^12];
    }

    public Machine(String filepath){
        sp = -1;
        pc = 0;
        done = false;
        load(filepath);
        stack = new int[2^8];
        globalMem = new int[2^12];
    }

    public Machine(){
        sp = -1;
        pc = 0;
        done = false;
        stack = new int[2^8];
        globalMem = new int[2^12];
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
        int i=0;
        while(!done){
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
                case JMP -> {
                    a = pop();
                    pc = a;
                }
                case JMPT -> {
                    b = pop();
                    a = pop();
                    pc = b==1? a:pc;
                }
                case JMPF -> {
                    b = pop();
                    a = pop();
                    pc = b==0? a:pc;
                }
                case CONST -> {
                    pc += 1;
                    push(programMemory[pc]);
                }
                case GLOAD -> {
                    a = pop(); //get the address
                    push(globalMem[a]);
                }
                case  GSTORE -> {
                    pc+=1;
                    a = pop();
                    globalMem[pc] = a;
                }
                case PRINT -> System.out.println(stack[sp]);
                case POP -> pop();
                case HALT -> done = true;
            }
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
        Machine m = new Machine();
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
        }
    }

}
