#include "../common.h"

.section .text.init
.global _start
_start:
    init_all_regs
    la sp, __BINARY_END_
    mv a0,tp
.L_update_sp:
    addi sp,sp,1024
    beqz a0, .L_program
    addi a0, a0, -1
    j .L_update_sp
.L_program:
    call main

    mv x3,a0

    j .