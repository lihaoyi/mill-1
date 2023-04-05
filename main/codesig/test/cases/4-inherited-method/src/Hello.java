package hello;

class Parent{
    public int used(){ return 2; }
}
public class Hello extends Parent{
    public static int main(){ return new Hello().used(); }

    public int unused(){return 1;}
}
/* EXPECTED TRANSITIVE
{
    "hello.Hello.main()I": [
        "hello.Hello#<init>()V",
        "hello.Parent#<init>()V",
        "hello.Parent#used()I",
        "hello.Hello#used()I"
    ]
}
*/
