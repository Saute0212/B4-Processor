#define N 10

//a/2^shift
int div(int a, int shift)
{
    int answer;
    answer = a << N;
    for(int i=0; i<shift; i++)
    {
        answer=answer >> 1;
    }
    return answer;
}

//a*b
int mul(int a, int b)
{
    int answer;
    if(a==0 || b==0)
    {
        answer = 0;
    } else {
        for(int i=0; i<b; i++)
        {
            answer += a;
        }
    }
    return answer;
}

int main()
{
    int num, num1, num2, num3[N];
    for(int i=0; i<N; i++)
    {
        for(int j=0; j<N; j++)
        {
            num1=div(j, i);
            num2=mul(i, j);
            num3[i]+=num1+num2;
        }
    }

    for(int i=0; i<N; i++)
    {
        num+=num3[i];
    }

    __asm__ volatile("li x26,7");
    return num;
}