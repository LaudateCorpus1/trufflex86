/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */
int main(void)
{
	unsigned long arg = 0x0123456789ABCDEF;
	unsigned long out = 0;
	__asm__("bsrq %%rax, %%rcx" : "=c"(out) : "a"(arg));
	return (out == 56);
}
