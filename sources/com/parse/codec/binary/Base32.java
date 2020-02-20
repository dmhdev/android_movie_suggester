package com.parse.codec.binary;

import android.support.p000v4.view.MotionEventCompat;

public class Base32 extends BaseNCodec {
    private static final int BITS_PER_ENCODED_BYTE = 5;
    private static final int BYTES_PER_ENCODED_BLOCK = 8;
    private static final int BYTES_PER_UNENCODED_BLOCK = 5;
    private static final byte[] CHUNK_SEPARATOR = {13, 10};
    private static final byte[] DECODE_TABLE = {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 63, -1, -1, 26, 27, 28, 29, 30, 31, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25};
    private static final byte[] ENCODE_TABLE = {65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 50, 51, 52, 53, 54, 55};
    private static final byte[] HEX_DECODE_TABLE = {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 63, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, -1, -1, -1, -1, -1, -1, -1, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32};
    private static final byte[] HEX_ENCODE_TABLE = {48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86};
    private static final int MASK_5BITS = 31;
    private long bitWorkArea;
    private final int decodeSize;
    private final byte[] decodeTable;
    private final int encodeSize;
    private final byte[] encodeTable;
    private final byte[] lineSeparator;

    public Base32() {
        this(false);
    }

    public Base32(boolean useHex) {
        this(0, null, useHex);
    }

    public Base32(int lineLength) {
        this(lineLength, CHUNK_SEPARATOR);
    }

    public Base32(int lineLength, byte[] lineSeparator2) {
        this(lineLength, lineSeparator2, false);
    }

    public Base32(int lineLength, byte[] lineSeparator2, boolean useHex) {
        super(5, 8, lineLength, lineSeparator2 == null ? 0 : lineSeparator2.length);
        if (useHex) {
            this.encodeTable = HEX_ENCODE_TABLE;
            this.decodeTable = HEX_DECODE_TABLE;
        } else {
            this.encodeTable = ENCODE_TABLE;
            this.decodeTable = DECODE_TABLE;
        }
        if (lineLength <= 0) {
            this.encodeSize = 8;
            this.lineSeparator = null;
        } else if (lineSeparator2 == null) {
            throw new IllegalArgumentException("lineLength " + lineLength + " > 0, but lineSeparator is null");
        } else if (containsAlphabetOrPad(lineSeparator2)) {
            throw new IllegalArgumentException("lineSeparator must not contain Base32 characters: [" + StringUtils.newStringUtf8(lineSeparator2) + "]");
        } else {
            this.encodeSize = lineSeparator2.length + 8;
            this.lineSeparator = new byte[lineSeparator2.length];
            System.arraycopy(lineSeparator2, 0, this.lineSeparator, 0, lineSeparator2.length);
        }
        this.decodeSize = this.encodeSize - 1;
    }

    /* access modifiers changed from: 0000 */
    public void decode(byte[] in, int inPos, int inAvail) {
        if (!this.eof) {
            if (inAvail < 0) {
                this.eof = true;
            }
            int i = 0;
            int inPos2 = inPos;
            while (true) {
                if (i >= inAvail) {
                    break;
                }
                int inPos3 = inPos2 + 1;
                byte b = in[inPos2];
                if (b == 61) {
                    this.eof = true;
                    break;
                }
                ensureBufferSize(this.decodeSize);
                if (b >= 0 && b < this.decodeTable.length) {
                    byte result = this.decodeTable[b];
                    if (result >= 0) {
                        this.modulus = (this.modulus + 1) % 8;
                        this.bitWorkArea = (this.bitWorkArea << 5) + ((long) result);
                        if (this.modulus == 0) {
                            byte[] bArr = this.buffer;
                            int i2 = this.pos;
                            this.pos = i2 + 1;
                            bArr[i2] = (byte) ((int) ((this.bitWorkArea >> 32) & 255));
                            byte[] bArr2 = this.buffer;
                            int i3 = this.pos;
                            this.pos = i3 + 1;
                            bArr2[i3] = (byte) ((int) ((this.bitWorkArea >> 24) & 255));
                            byte[] bArr3 = this.buffer;
                            int i4 = this.pos;
                            this.pos = i4 + 1;
                            bArr3[i4] = (byte) ((int) ((this.bitWorkArea >> 16) & 255));
                            byte[] bArr4 = this.buffer;
                            int i5 = this.pos;
                            this.pos = i5 + 1;
                            bArr4[i5] = (byte) ((int) ((this.bitWorkArea >> 8) & 255));
                            byte[] bArr5 = this.buffer;
                            int i6 = this.pos;
                            this.pos = i6 + 1;
                            bArr5[i6] = (byte) ((int) (this.bitWorkArea & 255));
                        }
                    }
                }
                i++;
                inPos2 = inPos3;
            }
            if (this.eof && this.modulus >= 2) {
                ensureBufferSize(this.decodeSize);
                switch (this.modulus) {
                    case 2:
                        byte[] bArr6 = this.buffer;
                        int i7 = this.pos;
                        this.pos = i7 + 1;
                        bArr6[i7] = (byte) ((int) ((this.bitWorkArea >> 2) & 255));
                        return;
                    case 3:
                        byte[] bArr7 = this.buffer;
                        int i8 = this.pos;
                        this.pos = i8 + 1;
                        bArr7[i8] = (byte) ((int) ((this.bitWorkArea >> 7) & 255));
                        return;
                    case 4:
                        this.bitWorkArea >>= 4;
                        byte[] bArr8 = this.buffer;
                        int i9 = this.pos;
                        this.pos = i9 + 1;
                        bArr8[i9] = (byte) ((int) ((this.bitWorkArea >> 8) & 255));
                        byte[] bArr9 = this.buffer;
                        int i10 = this.pos;
                        this.pos = i10 + 1;
                        bArr9[i10] = (byte) ((int) (this.bitWorkArea & 255));
                        return;
                    case 5:
                        this.bitWorkArea >>= 1;
                        byte[] bArr10 = this.buffer;
                        int i11 = this.pos;
                        this.pos = i11 + 1;
                        bArr10[i11] = (byte) ((int) ((this.bitWorkArea >> 16) & 255));
                        byte[] bArr11 = this.buffer;
                        int i12 = this.pos;
                        this.pos = i12 + 1;
                        bArr11[i12] = (byte) ((int) ((this.bitWorkArea >> 8) & 255));
                        byte[] bArr12 = this.buffer;
                        int i13 = this.pos;
                        this.pos = i13 + 1;
                        bArr12[i13] = (byte) ((int) (this.bitWorkArea & 255));
                        return;
                    case 6:
                        this.bitWorkArea >>= 6;
                        byte[] bArr13 = this.buffer;
                        int i14 = this.pos;
                        this.pos = i14 + 1;
                        bArr13[i14] = (byte) ((int) ((this.bitWorkArea >> 16) & 255));
                        byte[] bArr14 = this.buffer;
                        int i15 = this.pos;
                        this.pos = i15 + 1;
                        bArr14[i15] = (byte) ((int) ((this.bitWorkArea >> 8) & 255));
                        byte[] bArr15 = this.buffer;
                        int i16 = this.pos;
                        this.pos = i16 + 1;
                        bArr15[i16] = (byte) ((int) (this.bitWorkArea & 255));
                        return;
                    case MotionEventCompat.ACTION_HOVER_MOVE /*7*/:
                        this.bitWorkArea >>= 3;
                        byte[] bArr16 = this.buffer;
                        int i17 = this.pos;
                        this.pos = i17 + 1;
                        bArr16[i17] = (byte) ((int) ((this.bitWorkArea >> 24) & 255));
                        byte[] bArr17 = this.buffer;
                        int i18 = this.pos;
                        this.pos = i18 + 1;
                        bArr17[i18] = (byte) ((int) ((this.bitWorkArea >> 16) & 255));
                        byte[] bArr18 = this.buffer;
                        int i19 = this.pos;
                        this.pos = i19 + 1;
                        bArr18[i19] = (byte) ((int) ((this.bitWorkArea >> 8) & 255));
                        byte[] bArr19 = this.buffer;
                        int i20 = this.pos;
                        this.pos = i20 + 1;
                        bArr19[i20] = (byte) ((int) (this.bitWorkArea & 255));
                        return;
                    default:
                        return;
                }
            }
        }
    }

    /* access modifiers changed from: 0000 */
    /* JADX WARNING: Incorrect type for immutable var: ssa=byte, code=int, for r0v0, types: [byte, int] */
    /* JADX WARNING: Multi-variable type inference failed */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void encode(byte[] r11, int r12, int r13) {
        /*
            r10 = this;
            boolean r4 = r10.eof
            if (r4 == 0) goto L_0x0005
        L_0x0004:
            return
        L_0x0005:
            if (r13 >= 0) goto L_0x0276
            r4 = 1
            r10.eof = r4
            int r4 = r10.modulus
            if (r4 != 0) goto L_0x0012
            int r4 = r10.lineLength
            if (r4 == 0) goto L_0x0004
        L_0x0012:
            int r4 = r10.encodeSize
            r10.ensureBufferSize(r4)
            int r3 = r10.pos
            int r4 = r10.modulus
            switch(r4) {
                case 1: goto L_0x0044;
                case 2: goto L_0x00b8;
                case 3: goto L_0x013f;
                case 4: goto L_0x01d1;
                default: goto L_0x001e;
            }
        L_0x001e:
            int r4 = r10.currentLinePos
            int r5 = r10.pos
            int r5 = r5 - r3
            int r4 = r4 + r5
            r10.currentLinePos = r4
            int r4 = r10.lineLength
            if (r4 <= 0) goto L_0x0004
            int r4 = r10.currentLinePos
            if (r4 <= 0) goto L_0x0004
            byte[] r4 = r10.lineSeparator
            r5 = 0
            byte[] r6 = r10.buffer
            int r7 = r10.pos
            byte[] r8 = r10.lineSeparator
            int r8 = r8.length
            java.lang.System.arraycopy(r4, r5, r6, r7, r8)
            int r4 = r10.pos
            byte[] r5 = r10.lineSeparator
            int r5 = r5.length
            int r4 = r4 + r5
            r10.pos = r4
            goto L_0x0004
        L_0x0044:
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 3
            long r7 = r7 >> r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 2
            long r7 = r7 << r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            r6 = 61
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            r6 = 61
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            r6 = 61
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            r6 = 61
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            r6 = 61
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            r6 = 61
            r4[r5] = r6
            goto L_0x001e
        L_0x00b8:
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 11
            long r7 = r7 >> r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 6
            long r7 = r7 >> r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 1
            long r7 = r7 >> r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 4
            long r7 = r7 << r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            r6 = 61
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            r6 = 61
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            r6 = 61
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            r6 = 61
            r4[r5] = r6
            goto L_0x001e
        L_0x013f:
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 19
            long r7 = r7 >> r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 14
            long r7 = r7 >> r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 9
            long r7 = r7 >> r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 4
            long r7 = r7 >> r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 1
            long r7 = r7 << r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            r6 = 61
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            r6 = 61
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            r6 = 61
            r4[r5] = r6
            goto L_0x001e
        L_0x01d1:
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 27
            long r7 = r7 >> r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 22
            long r7 = r7 >> r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 17
            long r7 = r7 >> r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 12
            long r7 = r7 >> r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 7
            long r7 = r7 >> r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 2
            long r7 = r7 >> r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 3
            long r7 = r7 << r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            r6 = 61
            r4[r5] = r6
            goto L_0x001e
        L_0x0276:
            r1 = 0
            r2 = r12
        L_0x0278:
            if (r1 >= r13) goto L_0x0375
            int r4 = r10.encodeSize
            r10.ensureBufferSize(r4)
            int r4 = r10.modulus
            int r4 = r4 + 1
            int r4 = r4 % 5
            r10.modulus = r4
            int r12 = r2 + 1
            byte r0 = r11[r2]
            if (r0 >= 0) goto L_0x028f
            int r0 = r0 + 256
        L_0x028f:
            long r4 = r10.bitWorkArea
            r6 = 8
            long r4 = r4 << r6
            long r6 = (long) r0
            long r4 = r4 + r6
            r10.bitWorkArea = r4
            int r4 = r10.modulus
            if (r4 != 0) goto L_0x0370
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 35
            long r7 = r7 >> r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 30
            long r7 = r7 >> r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 25
            long r7 = r7 >> r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 20
            long r7 = r7 >> r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 15
            long r7 = r7 >> r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 10
            long r7 = r7 >> r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            r9 = 5
            long r7 = r7 >> r9
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            byte[] r4 = r10.buffer
            int r5 = r10.pos
            int r6 = r5 + 1
            r10.pos = r6
            byte[] r6 = r10.encodeTable
            long r7 = r10.bitWorkArea
            int r7 = (int) r7
            r7 = r7 & 31
            byte r6 = r6[r7]
            r4[r5] = r6
            int r4 = r10.currentLinePos
            int r4 = r4 + 8
            r10.currentLinePos = r4
            int r4 = r10.lineLength
            if (r4 <= 0) goto L_0x0370
            int r4 = r10.lineLength
            int r5 = r10.currentLinePos
            if (r4 > r5) goto L_0x0370
            byte[] r4 = r10.lineSeparator
            r5 = 0
            byte[] r6 = r10.buffer
            int r7 = r10.pos
            byte[] r8 = r10.lineSeparator
            int r8 = r8.length
            java.lang.System.arraycopy(r4, r5, r6, r7, r8)
            int r4 = r10.pos
            byte[] r5 = r10.lineSeparator
            int r5 = r5.length
            int r4 = r4 + r5
            r10.pos = r4
            r4 = 0
            r10.currentLinePos = r4
        L_0x0370:
            int r1 = r1 + 1
            r2 = r12
            goto L_0x0278
        L_0x0375:
            r12 = r2
            goto L_0x0004
        */
        throw new UnsupportedOperationException("Method not decompiled: com.parse.codec.binary.Base32.encode(byte[], int, int):void");
    }

    public boolean isInAlphabet(byte octet) {
        return octet >= 0 && octet < this.decodeTable.length && this.decodeTable[octet] != -1;
    }
}
