package hello;

public class Hello{
    public static int main(){ return used(); }
    public static int used(){ return 2; }
    public static int unused(){ return 1; }
}

/* EXPECTED CALL GRAPH
{
    "hello.Hello.main()int": ["hello.Hello.used()int"]
}
*/