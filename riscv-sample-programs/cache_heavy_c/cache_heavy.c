#define NOP8() __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;")
#define NOP32() do {NOP8(); NOP8(); NOP8(); NOP8(); } while(0)

int main()
{
    for(int i=0; i < 10; i++)
    {
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