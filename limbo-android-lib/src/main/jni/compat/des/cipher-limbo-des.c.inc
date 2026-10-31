/*
 * Limbo: built-in DES for VNC password authentication.
 *
 * QEMU 10.x/11.x builds that have neither gcrypt, nettle nor gnutls include
 * crypto/cipher-stub.c.inc, whose qcrypto_cipher_supports() always returns
 * false. ui/vnc.c validates the "password" option with
 * qcrypto_cipher_supports(QCRYPTO_CIPHER_ALGO_DES, ECB) and aborts the whole
 * VM ("Cipher backend does not support DES algorithm") when it is false, so
 * VNC password auth is unusable on this project's Android builds.
 *
 * This file is copied into the QEMU tree by scripts/fix_qemu11.py as
 * crypto/cipher-limbo-des.c.inc and included instead of cipher-stub.c.inc.
 * It provides the same two symbols the stub defines, with DES/ECB backed by
 * the D3DES implementation below.
 *
 * The D3DES core is the public-domain D3DES (V5.09) by Richard Outerbridge
 * with the double/triple length support removed for VNC (the change made by
 * AT&T Laboratories Cambridge for the original VNC code), i.e. the same code
 * QEMU 5.1.0 shipped as crypto/desrfb.c. Only the bit order of the key
 * schedule table was changed (see limbo_des_bytebit[] below): QEMU >= 8 byte-reverses
 * the VNC key itself in ui/vnc.c (vnc_munge_des_rfb_key()), so this code has
 * to implement standard DES rather than the pre-munged variant used by 5.1.
 *
 * Do not include this file directly: it is included from crypto/cipher.c,
 * which already provides qemu/osdep.h, qapi/error.h and cipherpriv.h.
 */

/* ------------------------------------------------------------------ */
/* D3DES core                                                          */
/* ------------------------------------------------------------------ */

#define LIMBO_DES_ENCRYPT 0     /* D3DES "EN0": build the encryption schedule */
#define LIMBO_DES_DECRYPT 1     /* D3DES "DE1": build the decryption schedule */

static void limbo_des_scrunch(unsigned char *, unsigned long *);
static void limbo_des_unscrun(unsigned long *, unsigned char *);
static void limbo_des_func(unsigned long *, const unsigned long *);
static void limbo_des_cookey(unsigned long *, unsigned long *);

static const unsigned short limbo_des_bytebit[8] = {
        /* MSB first: QEMU >= 8 already byte-reverses the key (VNC RFB) */
        0200, 0100, 040, 020, 010, 04, 02, 01 };

static const unsigned long limbo_des_bigbyte[24] = {
        0x800000L,	0x400000L,	0x200000L,	0x100000L,
        0x80000L,	0x40000L,	0x20000L,	0x10000L,
        0x8000L,	0x4000L,	0x2000L,	0x1000L,
        0x800L, 	0x400L, 	0x200L, 	0x100L,
        0x80L,		0x40L,		0x20L,		0x10L,
        0x8L,		0x4L,		0x2L,		0x1L	};

/* Use the key schedule specified in the Standard (ANSI X3.92-1981). */

static const unsigned char limbo_des_pc1[56] = {
        56, 48, 40, 32, 24, 16,  8,	 0, 57, 49, 41, 33, 25, 17,
         9,  1, 58, 50, 42, 34, 26,	18, 10,  2, 59, 51, 43, 35,
        62, 54, 46, 38, 30, 22, 14,	 6, 61, 53, 45, 37, 29, 21,
        13,  5, 60, 52, 44, 36, 28,	20, 12,  4, 27, 19, 11,  3 };

static const unsigned char limbo_des_totrot[16] = {
        1,2,4,6,8,10,12,14,15,17,19,21,23,25,27,28 };

static const unsigned char limbo_des_pc2[48] = {
        13, 16, 10, 23,  0,  4,  2, 27, 14,  5, 20,  9,
        22, 18, 11,  3, 25,  7, 15,  6, 26, 19, 12,  1,
        40, 51, 30, 36, 46, 54, 29, 39, 50, 44, 32, 47,
        43, 48, 38, 55, 33, 52, 45, 41, 49, 35, 28, 31 };

/* Thanks to James Gillogly & Phil Karn! */
static void limbo_des_key_schedule(const unsigned char *key, int edf,
                            unsigned long *schedule)
{
        register int i, j, l, m, n;
        unsigned char pc1m[56], pcr[56];
        unsigned long kn[32];

        for ( j = 0; j < 56; j++ ) {
                l = limbo_des_pc1[j];
                m = l & 07;
                pc1m[j] = (key[l >> 3] & limbo_des_bytebit[m]) ? 1 : 0;
                }
        for( i = 0; i < 16; i++ ) {
                if( edf == LIMBO_DES_DECRYPT ) m = (15 - i) << 1;
                else m = i << 1;
                n = m + 1;
                kn[m] = kn[n] = 0L;
                for( j = 0; j < 28; j++ ) {
                        l = j + limbo_des_totrot[i];
                        if( l < 28 ) pcr[j] = pc1m[l];
                        else pcr[j] = pc1m[l - 28];
                        }
                for( j = 28; j < 56; j++ ) {
                    l = j + limbo_des_totrot[i];
                    if( l < 56 ) pcr[j] = pc1m[l];
                    else pcr[j] = pc1m[l - 28];
                    }
                for( j = 0; j < 24; j++ ) {
                        if( pcr[limbo_des_pc2[j]] ) kn[m] |= limbo_des_bigbyte[j];
                        if( pcr[limbo_des_pc2[j+24]] ) kn[n] |= limbo_des_bigbyte[j];
                        }
                }
        limbo_des_cookey(kn, schedule);
        return;
        }

static void limbo_des_cookey(register unsigned long *raw1,
                            unsigned long *schedule)
{
        register unsigned long *cook, *raw0;
        unsigned long dough[32];
        register int i;

        cook = dough;
        for( i = 0; i < 16; i++, raw1++ ) {
                raw0 = raw1++;
                *cook	 = (*raw0 & 0x00fc0000L) << 6;
                *cook	|= (*raw0 & 0x00000fc0L) << 10;
                *cook	|= (*raw1 & 0x00fc0000L) >> 10;
                *cook++ |= (*raw1 & 0x00000fc0L) >> 6;
                *cook	 = (*raw0 & 0x0003f000L) << 12;
                *cook	|= (*raw0 & 0x0000003fL) << 16;
                *cook	|= (*raw1 & 0x0003f000L) >> 4;
                *cook++ |= (*raw1 & 0x0000003fL);
                }
        memcpy(schedule, dough, sizeof(dough));
        return;
        }


static void limbo_des_block(const unsigned long *schedule,
                     const unsigned char *inblock, unsigned char *outblock)
{
        unsigned long work[2];

        limbo_des_scrunch((unsigned char *)inblock, work);
        limbo_des_func(work, schedule);
        limbo_des_unscrun(work, outblock);
        return;
        }

static void limbo_des_scrunch(register unsigned char *outof,
                             register unsigned long *into)
{
        *into	 = (*outof++ & 0xffL) << 24;
        *into	|= (*outof++ & 0xffL) << 16;
        *into	|= (*outof++ & 0xffL) << 8;
        *into++ |= (*outof++ & 0xffL);
        *into	 = (*outof++ & 0xffL) << 24;
        *into	|= (*outof++ & 0xffL) << 16;
        *into	|= (*outof++ & 0xffL) << 8;
        *into	|= (*outof   & 0xffL);
        return;
        }

static void limbo_des_unscrun(register unsigned long *outof,
                             register unsigned char *into)
{
        *into++ = (unsigned char)((*outof >> 24) & 0xffL);
        *into++ = (unsigned char)((*outof >> 16) & 0xffL);
        *into++ = (unsigned char)((*outof >>  8) & 0xffL);
        *into++ = (unsigned char)(*outof++	 & 0xffL);
        *into++ = (unsigned char)((*outof >> 24) & 0xffL);
        *into++ = (unsigned char)((*outof >> 16) & 0xffL);
        *into++ = (unsigned char)((*outof >>  8) & 0xffL);
        *into	=  (unsigned char)(*outof	 & 0xffL);
        return;
        }

static const unsigned long limbo_des_sp1[64] = {
        0x01010400L, 0x00000000L, 0x00010000L, 0x01010404L,
        0x01010004L, 0x00010404L, 0x00000004L, 0x00010000L,
        0x00000400L, 0x01010400L, 0x01010404L, 0x00000400L,
        0x01000404L, 0x01010004L, 0x01000000L, 0x00000004L,
        0x00000404L, 0x01000400L, 0x01000400L, 0x00010400L,
        0x00010400L, 0x01010000L, 0x01010000L, 0x01000404L,
        0x00010004L, 0x01000004L, 0x01000004L, 0x00010004L,
        0x00000000L, 0x00000404L, 0x00010404L, 0x01000000L,
        0x00010000L, 0x01010404L, 0x00000004L, 0x01010000L,
        0x01010400L, 0x01000000L, 0x01000000L, 0x00000400L,
        0x01010004L, 0x00010000L, 0x00010400L, 0x01000004L,
        0x00000400L, 0x00000004L, 0x01000404L, 0x00010404L,
        0x01010404L, 0x00010004L, 0x01010000L, 0x01000404L,
        0x01000004L, 0x00000404L, 0x00010404L, 0x01010400L,
        0x00000404L, 0x01000400L, 0x01000400L, 0x00000000L,
        0x00010004L, 0x00010400L, 0x00000000L, 0x01010004L };

static const unsigned long limbo_des_sp2[64] = {
        0x80108020L, 0x80008000L, 0x00008000L, 0x00108020L,
        0x00100000L, 0x00000020L, 0x80100020L, 0x80008020L,
        0x80000020L, 0x80108020L, 0x80108000L, 0x80000000L,
        0x80008000L, 0x00100000L, 0x00000020L, 0x80100020L,
        0x00108000L, 0x00100020L, 0x80008020L, 0x00000000L,
        0x80000000L, 0x00008000L, 0x00108020L, 0x80100000L,
        0x00100020L, 0x80000020L, 0x00000000L, 0x00108000L,
        0x00008020L, 0x80108000L, 0x80100000L, 0x00008020L,
        0x00000000L, 0x00108020L, 0x80100020L, 0x00100000L,
        0x80008020L, 0x80100000L, 0x80108000L, 0x00008000L,
        0x80100000L, 0x80008000L, 0x00000020L, 0x80108020L,
        0x00108020L, 0x00000020L, 0x00008000L, 0x80000000L,
        0x00008020L, 0x80108000L, 0x00100000L, 0x80000020L,
        0x00100020L, 0x80008020L, 0x80000020L, 0x00100020L,
        0x00108000L, 0x00000000L, 0x80008000L, 0x00008020L,
        0x80000000L, 0x80100020L, 0x80108020L, 0x00108000L };

static const unsigned long limbo_des_sp3[64] = {
        0x00000208L, 0x08020200L, 0x00000000L, 0x08020008L,
        0x08000200L, 0x00000000L, 0x00020208L, 0x08000200L,
        0x00020008L, 0x08000008L, 0x08000008L, 0x00020000L,
        0x08020208L, 0x00020008L, 0x08020000L, 0x00000208L,
        0x08000000L, 0x00000008L, 0x08020200L, 0x00000200L,
        0x00020200L, 0x08020000L, 0x08020008L, 0x00020208L,
        0x08000208L, 0x00020200L, 0x00020000L, 0x08000208L,
        0x00000008L, 0x08020208L, 0x00000200L, 0x08000000L,
        0x08020200L, 0x08000000L, 0x00020008L, 0x00000208L,
        0x00020000L, 0x08020200L, 0x08000200L, 0x00000000L,
        0x00000200L, 0x00020008L, 0x08020208L, 0x08000200L,
        0x08000008L, 0x00000200L, 0x00000000L, 0x08020008L,
        0x08000208L, 0x00020000L, 0x08000000L, 0x08020208L,
        0x00000008L, 0x00020208L, 0x00020200L, 0x08000008L,
        0x08020000L, 0x08000208L, 0x00000208L, 0x08020000L,
        0x00020208L, 0x00000008L, 0x08020008L, 0x00020200L };

static const unsigned long limbo_des_sp4[64] = {
        0x00802001L, 0x00002081L, 0x00002081L, 0x00000080L,
        0x00802080L, 0x00800081L, 0x00800001L, 0x00002001L,
        0x00000000L, 0x00802000L, 0x00802000L, 0x00802081L,
        0x00000081L, 0x00000000L, 0x00800080L, 0x00800001L,
        0x00000001L, 0x00002000L, 0x00800000L, 0x00802001L,
        0x00000080L, 0x00800000L, 0x00002001L, 0x00002080L,
        0x00800081L, 0x00000001L, 0x00002080L, 0x00800080L,
        0x00002000L, 0x00802080L, 0x00802081L, 0x00000081L,
        0x00800080L, 0x00800001L, 0x00802000L, 0x00802081L,
        0x00000081L, 0x00000000L, 0x00000000L, 0x00802000L,
        0x00002080L, 0x00800080L, 0x00800081L, 0x00000001L,
        0x00802001L, 0x00002081L, 0x00002081L, 0x00000080L,
        0x00802081L, 0x00000081L, 0x00000001L, 0x00002000L,
        0x00800001L, 0x00002001L, 0x00802080L, 0x00800081L,
        0x00002001L, 0x00002080L, 0x00800000L, 0x00802001L,
        0x00000080L, 0x00800000L, 0x00002000L, 0x00802080L };

static const unsigned long limbo_des_sp5[64] = {
        0x00000100L, 0x02080100L, 0x02080000L, 0x42000100L,
        0x00080000L, 0x00000100L, 0x40000000L, 0x02080000L,
        0x40080100L, 0x00080000L, 0x02000100L, 0x40080100L,
        0x42000100L, 0x42080000L, 0x00080100L, 0x40000000L,
        0x02000000L, 0x40080000L, 0x40080000L, 0x00000000L,
        0x40000100L, 0x42080100L, 0x42080100L, 0x02000100L,
        0x42080000L, 0x40000100L, 0x00000000L, 0x42000000L,
        0x02080100L, 0x02000000L, 0x42000000L, 0x00080100L,
        0x00080000L, 0x42000100L, 0x00000100L, 0x02000000L,
        0x40000000L, 0x02080000L, 0x42000100L, 0x40080100L,
        0x02000100L, 0x40000000L, 0x42080000L, 0x02080100L,
        0x40080100L, 0x00000100L, 0x02000000L, 0x42080000L,
        0x42080100L, 0x00080100L, 0x42000000L, 0x42080100L,
        0x02080000L, 0x00000000L, 0x40080000L, 0x42000000L,
        0x00080100L, 0x02000100L, 0x40000100L, 0x00080000L,
        0x00000000L, 0x40080000L, 0x02080100L, 0x40000100L };

static const unsigned long limbo_des_sp6[64] = {
        0x20000010L, 0x20400000L, 0x00004000L, 0x20404010L,
        0x20400000L, 0x00000010L, 0x20404010L, 0x00400000L,
        0x20004000L, 0x00404010L, 0x00400000L, 0x20000010L,
        0x00400010L, 0x20004000L, 0x20000000L, 0x00004010L,
        0x00000000L, 0x00400010L, 0x20004010L, 0x00004000L,
        0x00404000L, 0x20004010L, 0x00000010L, 0x20400010L,
        0x20400010L, 0x00000000L, 0x00404010L, 0x20404000L,
        0x00004010L, 0x00404000L, 0x20404000L, 0x20000000L,
        0x20004000L, 0x00000010L, 0x20400010L, 0x00404000L,
        0x20404010L, 0x00400000L, 0x00004010L, 0x20000010L,
        0x00400000L, 0x20004000L, 0x20000000L, 0x00004010L,
        0x20000010L, 0x20404010L, 0x00404000L, 0x20400000L,
        0x00404010L, 0x20404000L, 0x00000000L, 0x20400010L,
        0x00000010L, 0x00004000L, 0x20400000L, 0x00404010L,
        0x00004000L, 0x00400010L, 0x20004010L, 0x00000000L,
        0x20404000L, 0x20000000L, 0x00400010L, 0x20004010L };

static const unsigned long limbo_des_sp7[64] = {
        0x00200000L, 0x04200002L, 0x04000802L, 0x00000000L,
        0x00000800L, 0x04000802L, 0x00200802L, 0x04200800L,
        0x04200802L, 0x00200000L, 0x00000000L, 0x04000002L,
        0x00000002L, 0x04000000L, 0x04200002L, 0x00000802L,
        0x04000800L, 0x00200802L, 0x00200002L, 0x04000800L,
        0x04000002L, 0x04200000L, 0x04200800L, 0x00200002L,
        0x04200000L, 0x00000800L, 0x00000802L, 0x04200802L,
        0x00200800L, 0x00000002L, 0x04000000L, 0x00200800L,
        0x04000000L, 0x00200800L, 0x00200000L, 0x04000802L,
        0x04000802L, 0x04200002L, 0x04200002L, 0x00000002L,
        0x00200002L, 0x04000000L, 0x04000800L, 0x00200000L,
        0x04200800L, 0x00000802L, 0x00200802L, 0x04200800L,
        0x00000802L, 0x04000002L, 0x04200802L, 0x04200000L,
        0x00200800L, 0x00000000L, 0x00000002L, 0x04200802L,
        0x00000000L, 0x00200802L, 0x04200000L, 0x00000800L,
        0x04000002L, 0x04000800L, 0x00000800L, 0x00200002L };

static const unsigned long limbo_des_sp8[64] = {
        0x10001040L, 0x00001000L, 0x00040000L, 0x10041040L,
        0x10000000L, 0x10001040L, 0x00000040L, 0x10000000L,
        0x00040040L, 0x10040000L, 0x10041040L, 0x00041000L,
        0x10041000L, 0x00041040L, 0x00001000L, 0x00000040L,
        0x10040000L, 0x10000040L, 0x10001000L, 0x00001040L,
        0x00041000L, 0x00040040L, 0x10040040L, 0x10041000L,
        0x00001040L, 0x00000000L, 0x00000000L, 0x10040040L,
        0x10000040L, 0x10001000L, 0x00041040L, 0x00040000L,
        0x00041040L, 0x00040000L, 0x10041000L, 0x00001000L,
        0x00000040L, 0x10040040L, 0x00001000L, 0x00041040L,
        0x10001000L, 0x00000040L, 0x10000040L, 0x10040000L,
        0x10040040L, 0x10000000L, 0x00040000L, 0x10001040L,
        0x00000000L, 0x10041040L, 0x00040040L, 0x10000040L,
        0x10040000L, 0x10001000L, 0x10001040L, 0x00000000L,
        0x10041040L, 0x00041000L, 0x00041000L, 0x00001040L,
        0x00001040L, 0x00040040L, 0x10000000L, 0x10041000L };

static void limbo_des_func(register unsigned long *block,
                          const unsigned long *keys)
{
        register unsigned long fval, work, right, leftt;
        register int round;

        leftt = block[0];
        right = block[1];
        work = ((leftt >> 4) ^ right) & 0x0f0f0f0fL;
        right ^= work;
        leftt ^= (work << 4);
        work = ((leftt >> 16) ^ right) & 0x0000ffffL;
        right ^= work;
        leftt ^= (work << 16);
        work = ((right >> 2) ^ leftt) & 0x33333333L;
        leftt ^= work;
        right ^= (work << 2);
        work = ((right >> 8) ^ leftt) & 0x00ff00ffL;
        leftt ^= work;
        right ^= (work << 8);
        right = ((right << 1) | ((right >> 31) & 1L)) & 0xffffffffL;
        work = (leftt ^ right) & 0xaaaaaaaaL;
        leftt ^= work;
        right ^= work;
        leftt = ((leftt << 1) | ((leftt >> 31) & 1L)) & 0xffffffffL;

        for( round = 0; round < 8; round++ ) {
                work  = (right << 28) | (right >> 4);
                work ^= *keys++;
                fval  = limbo_des_sp7[ work		 & 0x3fL];
                fval |= limbo_des_sp5[(work >>  8) & 0x3fL];
                fval |= limbo_des_sp3[(work >> 16) & 0x3fL];
                fval |= limbo_des_sp1[(work >> 24) & 0x3fL];
                work  = right ^ *keys++;
                fval |= limbo_des_sp8[ work		 & 0x3fL];
                fval |= limbo_des_sp6[(work >>  8) & 0x3fL];
                fval |= limbo_des_sp4[(work >> 16) & 0x3fL];
                fval |= limbo_des_sp2[(work >> 24) & 0x3fL];
                leftt ^= fval;
                work  = (leftt << 28) | (leftt >> 4);
                work ^= *keys++;
                fval  = limbo_des_sp7[ work		 & 0x3fL];
                fval |= limbo_des_sp5[(work >>  8) & 0x3fL];
                fval |= limbo_des_sp3[(work >> 16) & 0x3fL];
                fval |= limbo_des_sp1[(work >> 24) & 0x3fL];
                work  = leftt ^ *keys++;
                fval |= limbo_des_sp8[ work		 & 0x3fL];
                fval |= limbo_des_sp6[(work >>  8) & 0x3fL];
                fval |= limbo_des_sp4[(work >> 16) & 0x3fL];
                fval |= limbo_des_sp2[(work >> 24) & 0x3fL];
                right ^= fval;
                }

        right = (right << 31) | (right >> 1);
        work = (leftt ^ right) & 0xaaaaaaaaL;
        leftt ^= work;
        right ^= work;
        leftt = (leftt << 31) | (leftt >> 1);
        work = ((leftt >> 8) ^ right) & 0x00ff00ffL;
        right ^= work;
        leftt ^= (work << 8);
        work = ((leftt >> 2) ^ right) & 0x33333333L;
        right ^= work;
        leftt ^= (work << 2);
        work = ((right >> 16) ^ leftt) & 0x0000ffffL;
        leftt ^= work;
        right ^= (work << 16);
        work = ((right >> 4) ^ leftt) & 0x0f0f0f0fL;
        leftt ^= work;
        right ^= (work << 4);
        *block++ = right;
        *block = leftt;
        return;
        }

/* ------------------------------------------------------------------ */
/* QCryptoCipher glue                                                  */
/* ------------------------------------------------------------------ */

typedef struct QCryptoCipherLimboDES {
    QCryptoCipher base;
    unsigned long schedule_enc[32];
    unsigned long schedule_dec[32];
} QCryptoCipherLimboDES;

static void limbo_des_ctx_free(QCryptoCipher *cipher)
{
    QCryptoCipherLimboDES *ctx =
        container_of(cipher, QCryptoCipherLimboDES, base);

    g_free(ctx);
}

static int limbo_des_ctx_encrypt(QCryptoCipher *cipher,
                                 const void *in,
                                 void *out,
                                 size_t len,
                                 Error **errp)
{
    QCryptoCipherLimboDES *ctx =
        container_of(cipher, QCryptoCipherLimboDES, base);
    const uint8_t *src = in;
    uint8_t *dst = out;
    size_t i;

    if (len & 7) {
        error_setg(errp, "Length %zu must be a multiple of the DES block "
                   "size 8", len);
        return -1;
    }

    for (i = 0; i < len; i += 8) {
        limbo_des_block(ctx->schedule_enc, src + i, dst + i);
    }

    return 0;
}

static int limbo_des_ctx_decrypt(QCryptoCipher *cipher,
                                 const void *in,
                                 void *out,
                                 size_t len,
                                 Error **errp)
{
    QCryptoCipherLimboDES *ctx =
        container_of(cipher, QCryptoCipherLimboDES, base);
    const uint8_t *src = in;
    uint8_t *dst = out;
    size_t i;

    if (len & 7) {
        error_setg(errp, "Length %zu must be a multiple of the DES block "
                   "size 8", len);
        return -1;
    }

    for (i = 0; i < len; i += 8) {
        limbo_des_block(ctx->schedule_dec, src + i, dst + i);
    }

    return 0;
}

static int limbo_des_ctx_setiv(QCryptoCipher *cipher,
                               const uint8_t *iv, size_t niv,
                               Error **errp)
{
    (void)cipher;
    (void)iv;
    (void)niv;
    error_setg(errp, "Setting an IV is not supported for DES-ECB");
    return -1;
}

static const QCryptoCipherDriver limbo_des_driver = {
    .cipher_encrypt = limbo_des_ctx_encrypt,
    .cipher_decrypt = limbo_des_ctx_decrypt,
    .cipher_setiv = limbo_des_ctx_setiv,
    .cipher_free = limbo_des_ctx_free,
};

bool qcrypto_cipher_supports(QCryptoCipherAlgo alg,
                             QCryptoCipherMode mode)
{
    /* Only DES is provided here; the rest of the crypto subsystem keeps
     * behaving exactly like the stub it replaces. */
    return alg == QCRYPTO_CIPHER_ALGO_DES && mode == QCRYPTO_CIPHER_MODE_ECB;
}

static QCryptoCipher *qcrypto_cipher_ctx_new(QCryptoCipherAlgo alg,
                                             QCryptoCipherMode mode,
                                             const uint8_t *key,
                                             size_t nkey,
                                             Error **errp)
{
    QCryptoCipherLimboDES *ctx;

    if (!qcrypto_cipher_validate_key_length(alg, mode, nkey, errp)) {
        return NULL;
    }

    if (alg != QCRYPTO_CIPHER_ALGO_DES || mode != QCRYPTO_CIPHER_MODE_ECB) {
        error_setg(errp,
                   "Unsupported cipher algorithm %s, no crypto library "
                   "enabled in build (only the built-in DES backend is "
                   "available)",
                   QCryptoCipherAlgo_str(alg));
        return NULL;
    }

    ctx = g_new0(QCryptoCipherLimboDES, 1);
    limbo_des_key_schedule(key, LIMBO_DES_ENCRYPT, ctx->schedule_enc);
    limbo_des_key_schedule(key, LIMBO_DES_DECRYPT, ctx->schedule_dec);
    ctx->base.driver = &limbo_des_driver;

    return &ctx->base;
}
