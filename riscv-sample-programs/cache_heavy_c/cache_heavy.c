#define N 100

int div(int a, int b)
{
    int tmp = 0;
    tmp = a-b;
    return tmp;
}

int mul(int a, int b)
{
    int tmp = 0;
    tmp = a+b;
    return tmp;
}

int main()
{
    //"input1" < "input2"
    int input1[N][N];
    int input2[N][N];
    int tmp1[N][N];
    int tmp2[N][N];
    int output[N][N];

    for(int run_count = 0; run_count < 3; run_count++)
    {
        for(int i = 0; i < N; i++)
        {
            for(int j = 0; j < N; j++)
            {
                input1[i][j] = i+1;
                input2[i][j] = i+j+2;
            }
        }

        for(int i = 0; i < N; i++)
        {
            for(int j = 0; j < N; j++)
            {
                tmp1[i][j] = div(input1[i][j], input2[i][j]);
                tmp2[i][j] = mul(input1[i][j], input2[i][j]);
            }
        }

        for(int i = 0; i < N; i++)
        {
            for(int j = 0; j < N; j++)
            {
                output[i][j] = tmp1[i][j]+tmp2[i][j];
            }
        }
    }

    return output[5][7];
}