int main()
{
    for(int i=0; i < 10; i++)
    {
        //Other programs are using 2Set*1Way in front of this code.
        //"nop":128bit*4
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");

        //"nop":128bit*4
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");

        //"nop":128bit*4
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");

        //"nop":128bit*4
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");

        //"nop":128bit*4
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");

        //"nop":128bit*4
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");

        //"nop":128bit*4
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");

        //"nop":128bit*4
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");

        //"nop":128bit*4
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");

        //"nop":128bit*4
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
        __asm__ volatile("nop;nop;nop;nop;nop;nop;nop;nop;");
    }

    __asm__ volatile("li x24,6;");

    return 0;
}
