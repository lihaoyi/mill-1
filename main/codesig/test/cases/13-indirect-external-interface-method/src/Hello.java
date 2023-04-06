package hello;
class Foo extends java.io.ByteArrayInputStream{
    public Foo() throws java.io.IOException{
        super(new byte[]{});
    }

    public int read(){
        return readSpecial();
    }
    public int readSpecial(){
        return 1337;
    }
}
public class Hello{
    public static int main() throws java.io.IOException{
        java.io.InputStream is = new Foo();
        return is.read();
    }
}

/* EXPECTED TRANSITIVE
{
    "hello.Foo#read()I": [
        "hello.Foo#readSpecial()I"
    ],
    "hello.Hello.main()I": [
        "hello.Foo#<init>()V",
        "hello.Foo#read()I",
        "hello.Foo#readSpecial()I"
    ]
}
*/
