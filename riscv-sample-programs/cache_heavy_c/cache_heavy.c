#define NOP8() __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;") //128bit
#define NOP32() do {NOP8(); NOP8(); NOP8(); NOP8(); } while(0) //512bit

int main()
{
    for(int i=0; i < 10; i++)
    {
        //512bit*10
        NOP32();
        NOP32();   
        NOP32();
        NOP32();
        NOP32();
        NOP32();
        NOP32();
        NOP32();
        NOP32();
        NOP32();
    }

    __asm__ volatile("li x24,6;");

    return 0;
}